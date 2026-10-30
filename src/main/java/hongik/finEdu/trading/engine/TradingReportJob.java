package hongik.finEdu.trading.engine;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.entity.DailyAssetSnapshot;
import hongik.finEdu.trading.entity.TradingAccount;
import hongik.finEdu.trading.entity.TradingHolding;
import hongik.finEdu.trading.repository.CashLedgerRepository;
import hongik.finEdu.trading.repository.DailyAssetSnapshotRepository;
import hongik.finEdu.trading.repository.TradeHistoryRepository;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import hongik.finEdu.trading.repository.TradingOrderRepository;
import hongik.finEdu.trading.service.MarketClock;
import hongik.finEdu.trading.service.StockPriceService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 장 마감 후 배치: 자산 스냅샷 · 수익률 랭킹 · 정합성 검증.
 *
 * <h3>정합성 불변식</h3>
 * <ol>
 *   <li>account.cash == Σ cash_ledger.delta</li>
 *   <li>account.lockedCash == Σ 미체결 매수 주문 lockedAmount</li>
 *   <li>holding.lockedQuantity == Σ 미체결 매도 주문 잔량</li>
 *   <li>holding.quantity == Σ 체결(매수 − 매도) — 원장 도입 이전 데이터는 체결내역 기준</li>
 * </ol>
 * 위반 시 ERROR 로그 + trading.reconciliation.mismatch 메트릭 (Grafana 알림 대상).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingReportJob {

    public static final String RANKING_KEY = "finEdu:trading:ranking:";

    private final TradingAccountRepository accountRepository;
    private final TradingHoldingRepository holdingRepository;
    private final TradingOrderRepository orderRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final CashLedgerRepository ledgerRepository;
    private final DailyAssetSnapshotRepository snapshotRepository;
    private final StockPriceService stockPriceService;
    private final MarketClock marketClock;
    private final StringRedisTemplate redis;
    private final MeterRegistry meterRegistry;
    private final TransactionTemplate transactionTemplate;

    /** 게이지는 강한 참조를 유지해야 GC 되지 않는다 */
    private final AtomicInteger mismatchUsers = new AtomicInteger();

    public void runEndOfDay() {
        LocalDate today = marketClock.today();
        Map<String, BigDecimal> priceByCode = new HashMap<>();
        TradingConstants.allStocks().forEach(s -> {
            try {
                priceByCode.put(s.code(), stockPriceService.getQuote(s.code()).currentPrice());
            } catch (Exception e) {
                log.warn("[EOD] {} 종가 조회 실패", s.code());
            }
        });

        int snapshots = 0;
        int mismatches = 0;
        for (String userId : accountRepository.findAllUserIds()) {
            try {
                Boolean ok = transactionTemplate.execute(st -> snapshotAndCheck(userId, today, priceByCode));
                snapshots++;
                if (Boolean.FALSE.equals(ok)) {
                    mismatches++;
                }
            } catch (Exception e) {
                log.warn("[EOD] userId={} 처리 실패: {}", userId, e.getMessage());
            }
        }
        meterRegistry.gauge("trading.reconciliation.mismatch.users", mismatchUsers).set(mismatches);
        log.info("[EOD] {} 스냅샷 {}명, 정합성 불일치 {}명", today, snapshots, mismatches);
    }

    /** @return 정합성 통과 여부 */
    boolean snapshotAndCheck(String userId, LocalDate date, Map<String, BigDecimal> priceByCode) {
        TradingAccount account = accountRepository.findByUserId(userId).orElse(null);
        if (account == null) {
            return true;
        }
        List<TradingHolding> holdings = holdingRepository.findByUserIdOrderByStockCodeAsc(userId);
        BigDecimal stockEval = holdings.stream()
                .map(h -> priceByCode.getOrDefault(h.getStockCode(), h.getAvgPrice())
                        .multiply(BigDecimal.valueOf(h.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = account.getCash().add(stockEval);
        BigDecimal rate = total.subtract(TradingConstants.INITIAL_CASH)
                .multiply(BigDecimal.valueOf(100))
                .divide(TradingConstants.INITIAL_CASH, 4, RoundingMode.HALF_UP);
        BigDecimal realized = tradeHistoryRepository.sumRealizedPnl(userId);

        if (snapshotRepository.findByUserIdAndSnapshotDate(userId, date).isEmpty()) {
            snapshotRepository.save(DailyAssetSnapshot.builder()
                    .userId(userId)
                    .snapshotDate(date)
                    .cash(account.getCash())
                    .stockEvaluation(stockEval)
                    .totalAsset(total)
                    .realizedPnl(realized)
                    .profitRate(rate)
                    .build());
        }
        try {
            redis.opsForZSet().add(RANKING_KEY + date, userId, rate.doubleValue());
            redis.opsForValue().set(RANKING_KEY + "latest", date.toString());
        } catch (Exception e) {
            log.warn("[EOD] 랭킹 저장 실패: {}", e.getMessage());
        }
        return reconcile(userId, account, holdings);
    }

    boolean reconcile(String userId, TradingAccount account, List<TradingHolding> holdings) {
        boolean ok = true;
        var open = EnumSet.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED);

        if (ledgerRepository.existsByUserId(userId)) {
            BigDecimal ledger = ledgerRepository.sumDelta(userId);
            if (ledger.compareTo(account.getCash()) != 0) {
                ok = mismatch(userId, "cash", account.getCash(), ledger);
            }
        }
        BigDecimal locked = orderRepository.sumLockedAmount(userId, open);
        if (locked.compareTo(account.getLockedCash()) != 0) {
            ok = mismatch(userId, "lockedCash", account.getLockedCash(), locked);
        }
        Map<String, Long> net = new HashMap<>();
        for (Object[] row : tradeHistoryRepository.netQuantityByStock(userId)) {
            net.put((String) row[0], ((Number) row[1]).longValue());
        }
        for (TradingHolding h : holdings) {
            long lockedQty = orderRepository.sumOpenSellQuantity(userId, h.getStockCode(), open);
            if (lockedQty != h.getLockedQuantity()) {
                ok = mismatch(userId, "lockedQty:" + h.getStockCode(), h.getLockedQuantity(), lockedQty);
            }
            Long expected = net.get(h.getStockCode());
            if (expected != null && expected != h.getQuantity()) {
                ok = mismatch(userId, "qty:" + h.getStockCode(), h.getQuantity(), expected);
            }
        }
        return ok;
    }

    private boolean mismatch(String userId, String field, Object actual, Object expected) {
        log.error("[정합성] userId={} {} 불일치: 실제={} 기대={}", userId, field, actual, expected);
        meterRegistry.counter("trading.reconciliation.mismatch", "field", field.split(":")[0]).increment();
        return false;
    }
}
