package hongik.finEdu.trading.engine;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.support.IntegrationTestSupport;
import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;
import hongik.finEdu.trading.entity.TradingAccount;
import hongik.finEdu.trading.entity.TradingHolding;
import hongik.finEdu.trading.entity.TradingOrder;
import hongik.finEdu.trading.repository.CashLedgerRepository;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 체결 엔진 정합성·동시성 테스트.
 * 고정 시계: 2026-10-29 10:00 (접속매매), 삼성전자 70,000원, 근사 호가(단계별 1,000주).
 */
class TradingEngineIntegrationTest extends IntegrationTestSupport {

    private static final String SAMSUNG = "005930";

    @Autowired OrderExecutionService executionService;
    @Autowired TradingAccountRepository accountRepository;
    @Autowired TradingHoldingRepository holdingRepository;
    @Autowired CashLedgerRepository ledgerRepository;
    @Autowired TradingReportJob reportJob;

    private static String newUser() {
        return "it-" + UUID.randomUUID();
    }

    private ExecutionReport buyMarket(String user, int qty) {
        return executionService.placeOrder(new PlaceOrderCommand(user, SAMSUNG, OrderType.BUY, PriceType.MARKET, qty, null, null, null));
    }

    @Test
    void 시장가_매수는_수수료_절사_포함_원장과_예수금이_일치한다() {
        String user = newUser();
        ExecutionReport r = buyMarket(user, 10);

        assertThat(r.order().getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(r.amountNow()).isEqualByComparingTo("700000");   // 최우선 매도호가 = 현재가
        assertThat(r.feeNow()).isEqualByComparingTo("105");         // 700,000 × 0.015% = 105
        TradingAccount acc = accountRepository.findByUserId(user).orElseThrow();
        assertThat(acc.getCash()).isEqualByComparingTo("9299895");
        assertThat(ledgerRepository.sumDelta(user)).isEqualByComparingTo(acc.getCash());
    }

    @Test
    void 지정가_매도는_수량을_lock_하고_취소하면_복원된다() {
        String user = newUser();
        buyMarket(user, 10);

        ExecutionReport sell = executionService.placeOrder(new PlaceOrderCommand(
                user, SAMSUNG, OrderType.SELL, PriceType.LIMIT, 4, new BigDecimal("75000"), null, null));
        assertThat(sell.order().getStatus()).isEqualTo(OrderStatus.PENDING);

        TradingHolding h = holdingRepository.findByUserIdAndStockCode(user, SAMSUNG).orElseThrow();
        assertThat(h.getQuantity()).isEqualTo(10);          // 보유수량은 그대로
        assertThat(h.getLockedQuantity()).isEqualTo(4);
        assertThat(h.getSellableQuantity()).isEqualTo(6);

        executionService.cancel(user, sell.order().getId());
        assertThat(holdingRepository.findByUserIdAndStockCode(user, SAMSUNG).orElseThrow().getLockedQuantity()).isZero();
    }

    @Test
    void 지정가_매수는_금액을_lock_하고_취소하면_주문가능금액이_복원된다() {
        String user = newUser();
        ExecutionReport buy = executionService.placeOrder(new PlaceOrderCommand(
                user, SAMSUNG, OrderType.BUY, PriceType.LIMIT, 10, new BigDecimal("65000"), null, null));
        TradingAccount acc = accountRepository.findByUserId(user).orElseThrow();
        assertThat(acc.getCash()).isEqualByComparingTo("10000000");       // 예수금 불변
        assertThat(acc.getLockedCash()).isEqualByComparingTo("650097");   // 650,000 + 97(절사)

        executionService.cancel(user, buy.order().getId());
        assertThat(accountRepository.findByUserId(user).orElseThrow().getLockedCash()).isEqualByComparingTo("0");
    }

    @Test
    void 호가단위_위반과_가격제한폭_초과는_거부() {
        String user = newUser();
        assertThatThrownBy(() -> executionService.placeOrder(new PlaceOrderCommand(
                user, SAMSUNG, OrderType.BUY, PriceType.LIMIT, 1, new BigDecimal("70050"), null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("호가");
        assertThatThrownBy(() -> executionService.placeOrder(new PlaceOrderCommand(
                user, SAMSUNG, OrderType.BUY, PriceType.LIMIT, 1, new BigDecimal("95000"), null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("가격제한폭");
    }

    @Test
    void 같은_멱등키로_두_번_주문하면_한_건만_체결() {
        String user = newUser();
        PlaceOrderCommand cmd = new PlaceOrderCommand(user, SAMSUNG, OrderType.BUY, PriceType.MARKET, 1, null, "key-1", null);
        ExecutionReport first = executionService.placeOrder(cmd);
        ExecutionReport second = executionService.placeOrder(cmd);
        assertThat(second.replayed()).isTrue();
        assertThat(second.order().getId()).isEqualTo(first.order().getId());
        assertThat(holdingRepository.findByUserIdAndStockCode(user, SAMSUNG).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    void 동시_매도_요청에도_보유수량을_초과해_팔리지_않는다() throws Exception {
        String user = newUser();
        buyMarket(user, 10);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    executionService.placeOrder(new PlaceOrderCommand(user, SAMSUNG, OrderType.SELL, PriceType.MARKET, 1, null, null, null));
                    ok.incrementAndGet();
                } catch (BusinessException expected) {
                    // 매도가능수량 부족
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(ok.get()).isEqualTo(10);
        assertThat(holdingRepository.findByUserIdAndStockCode(user, SAMSUNG)).isEmpty();
        TradingAccount acc = accountRepository.findByUserId(user).orElseThrow();
        assertThat(ledgerRepository.sumDelta(user)).isEqualByComparingTo(acc.getCash());
    }

    @Test
    void 동시_매수에도_예수금이_음수가_되지_않고_원장과_일치한다() throws Exception {
        String user = newUser();
        // 1주 70,010원(수수료 포함) → 1천만원으로 최대 142주. 200건 동시 요청.
        int threads = 200;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    buyMarket(user, 1);
                    ok.incrementAndGet();
                } catch (BusinessException expected) {
                    // 잔고 부족 또는 동시성 충돌
                } catch (RuntimeException ignored) {
                    // 락 대기 중 충돌 등
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        TradingAccount acc = accountRepository.findByUserId(user).orElseThrow();
        TradingHolding h = holdingRepository.findByUserIdAndStockCode(user, SAMSUNG).orElseThrow();
        assertThat(acc.getCash().signum()).isGreaterThanOrEqualTo(0);
        assertThat(h.getQuantity()).isEqualTo(ok.get());
        assertThat(ledgerRepository.sumDelta(user)).isEqualByComparingTo(acc.getCash());
        assertThat(reportJob.reconcile(user, acc, List.of(h))).isTrue();
    }

    @Test
    void 매도_체결은_실현손익을_기록한다() {
        String user = newUser();
        buyMarket(user, 10);
        ExecutionReport sell = executionService.placeOrder(new PlaceOrderCommand(
                user, SAMSUNG, OrderType.SELL, PriceType.MARKET, 10, null, null, null));
        // 근사 호가: 매수1호가 = 69,900 → 699,000, 수수료 104, 거래세 0.20% = 1,398
        assertThat(sell.amountNow()).isEqualByComparingTo("699000");
        assertThat(sell.taxNow()).isEqualByComparingTo("1398");
        TradingOrder o = sell.order();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.FILLED);
    }
}
