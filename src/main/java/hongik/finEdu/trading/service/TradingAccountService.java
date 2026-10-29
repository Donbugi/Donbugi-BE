package hongik.finEdu.trading.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.trading.client.StockQuote;
import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.dto.AccountSnapshotDto;
import hongik.finEdu.trading.dto.AccountSummaryDto;
import hongik.finEdu.trading.dto.AssetHistoryPointDto;
import hongik.finEdu.trading.dto.RankingEntryDto;
import hongik.finEdu.trading.dto.RankingResponse;
import hongik.finEdu.trading.engine.TradingReportJob;
import hongik.finEdu.trading.repository.DailyAssetSnapshotRepository;
import hongik.finEdu.user.repository.AppUserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.LocalDate;
import hongik.finEdu.trading.dto.HoldingDto;
import hongik.finEdu.trading.dto.StockDetailDto;
import hongik.finEdu.trading.dto.StockSummaryDto;
import hongik.finEdu.trading.dto.TradeHistoryDto;
import hongik.finEdu.trading.entity.TradeHistory;
import hongik.finEdu.trading.entity.TradingAccount;
import hongik.finEdu.trading.entity.TradingHolding;
import hongik.finEdu.trading.repository.TradeHistoryRepository;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TradingAccountService {

    private final StockPriceService stockPriceService;
    private final MarketClock marketClock;
    private final DailyAssetSnapshotRepository snapshotRepository;
    private final AppUserRepository appUserRepository;
    private final StringRedisTemplate redis;
    private final TradingAccountRepository accountRepository;
    private final TradingHoldingRepository holdingRepository;
    private final TradeHistoryRepository tradeHistoryRepository;

    @Transactional(readOnly = true)
    public List<StockSummaryDto> listStocks() {
        return stockPriceService.getAllStockQuotes().stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public StockDetailDto getStockDetail(String stockCode) {
        TradingConstants.requireStock(stockCode);
        StockQuote quote = stockPriceService.getQuoteDetail(stockCode);
        return new StockDetailDto(
                quote.stockCode(),
                quote.stockName(),
                quote.currentPrice(),
                quote.openPrice(),
                quote.highPrice(),
                quote.lowPrice(),
                quote.volume(),
                quote.changeRate(),
                quote.changePrice(),
                quote.direction(),
                quote.marketStatus()
        );
    }

    @Transactional(readOnly = true)
    public AccountSummaryDto getAccountSummary(String userId) {
        String uid = requireUserId(userId);
        TradingAccount account = accountRepository.findByUserId(uid).orElse(null);
        BigDecimal cash = account == null ? TradingConstants.INITIAL_CASH : account.getCash();
        BigDecimal locked = account == null ? BigDecimal.ZERO : account.getLockedCash();
        List<HoldingDto> holdings = listHoldings(uid);

        BigDecimal stockEval = holdings.stream()
                .map(HoldingDto::evaluationAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal evalProfit = holdings.stream()
                .map(HoldingDto::profit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAsset = cash.add(stockEval);
        BigDecimal profit = totalAsset.subtract(TradingConstants.INITIAL_CASH);
        BigDecimal profitRate = profit.multiply(BigDecimal.valueOf(100))
                .divide(TradingConstants.INITIAL_CASH, 2, RoundingMode.HALF_UP);

        return new AccountSummaryDto(
                uid,
                cash,
                stockEval,
                totalAsset,
                profit,
                profitRate,
                locked,
                cash.subtract(locked),
                tradeHistoryRepository.sumRealizedPnl(uid),
                evalProfit
        );
    }

    /** 일별 자산 추이 (장 마감 스냅샷 기준) */
    @Transactional(readOnly = true)
    public List<AssetHistoryPointDto> getAssetHistory(String userId, int days) {
        String uid = requireUserId(userId);
        LocalDate to = marketClock.today();
        LocalDate from = to.minusDays(Math.min(Math.max(days, 1), 365));
        return snapshotRepository.findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(uid, from, to).stream()
                .map(s -> new AssetHistoryPointDto(s.getSnapshotDate(), s.getTotalAsset(), s.getProfitRate(), s.getRealizedPnl()))
                .toList();
    }

    /** 수익률 랭킹 (최근 장 마감 기준, Redis ZSET) */
    public RankingResponse getRanking(String userId, int top) {
        String latest = redis.opsForValue().get(TradingReportJob.RANKING_KEY + "latest");
        if (latest == null) {
            return new RankingResponse(null, List.of(), null);
        }
        String key = TradingReportJob.RANKING_KEY + latest;
        int n = Math.min(Math.max(top, 1), 100);
        List<RankingEntryDto> entries = new java.util.ArrayList<>();
        var tuples = redis.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
        int rank = 0;
        if (tuples != null) {
            for (var t : tuples) {
                rank++;
                String uid = t.getValue();
                entries.add(new RankingEntryDto(rank, nicknameOf(uid), scoreOf(t.getScore()), uid != null && uid.equals(userId)));
            }
        }
        RankingEntryDto mine = null;
        if (userId != null) {
            Long r = redis.opsForZSet().reverseRank(key, userId);
            Double score = redis.opsForZSet().score(key, userId);
            if (r != null && score != null) {
                mine = new RankingEntryDto(r.intValue() + 1, nicknameOf(userId), scoreOf(score), true);
            }
        }
        return new RankingResponse(LocalDate.parse(latest), entries, mine);
    }

    private String nicknameOf(String userId) {
        return appUserRepository.findByExternalUserId(userId)
                .map(u -> u.getNickname() != null && !u.getNickname().isBlank() ? u.getNickname() : "익명")
                .orElse("익명");
    }

    private static BigDecimal scoreOf(Double score) {
        return score == null ? BigDecimal.ZERO : BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public AccountSnapshotDto getAccountSnapshot(String userId) {
        String uid = requireUserId(userId);
        return new AccountSnapshotDto(
                getAccountSummary(uid),
                listHoldings(uid),
                listStocks(),
                marketClock.isMarketOpen()
        );
    }

    @Transactional(readOnly = true)
    public List<HoldingDto> listHoldings(String userId) {
        String uid = requireUserId(userId);
        return holdingRepository.findByUserIdOrderByStockCodeAsc(uid).stream()
                .map(this::toHoldingDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public HoldingDto getHolding(String userId, String stockCode) {
        String uid = requireUserId(userId);
        TradingConstants.requireStock(stockCode);
        TradingHolding holding = holdingRepository.findByUserIdAndStockCode(uid, stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY, "보유 종목 없음"));
        return toHoldingDto(holding);
    }

    @Transactional(readOnly = true)
    public Page<TradeHistoryDto> listTrades(String userId, int page, int size, String stockCode) {
        String uid = requireUserId(userId);
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100));
        Page<TradeHistory> result;
        if (stockCode != null && !stockCode.isBlank()) {
            TradingConstants.requireStock(stockCode.trim());
            result = tradeHistoryRepository.findByUserIdAndStockCodeOrderByTradedAtDesc(
                    uid, stockCode.trim(), pageable);
        } else {
            result = tradeHistoryRepository.findByUserIdOrderByTradedAtDesc(uid, pageable);
        }
        return result.map(this::toTradeDto);
    }

    private HoldingDto toHoldingDto(TradingHolding holding) {
        StockQuote quote = stockPriceService.getQuote(holding.getStockCode());
        BigDecimal current = quote.currentPrice();
        BigDecimal eval = current.multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal profit = current.subtract(holding.getAvgPrice())
                .multiply(BigDecimal.valueOf(holding.getQuantity()));
        BigDecimal profitRate = current.subtract(holding.getAvgPrice())
                .multiply(BigDecimal.valueOf(100))
                .divide(holding.getAvgPrice(), 2, RoundingMode.HALF_UP);
        String name = TradingConstants.findStock(holding.getStockCode())
                .map(s -> s.name())
                .orElse(quote.stockName());

        return new HoldingDto(
                holding.getStockCode(),
                name,
                holding.getQuantity(),
                holding.getAvgPrice(),
                current,
                eval,
                profit,
                profitRate,
                holding.getLockedQuantity(),
                holding.getSellableQuantity()
        );
    }

    private StockSummaryDto toSummary(StockQuote quote) {
        return new StockSummaryDto(
                quote.stockCode(),
                quote.stockName(),
                quote.currentPrice(),
                quote.changeRate(),
                quote.changePrice(),
                quote.direction()
        );
    }

    private TradeHistoryDto toTradeDto(TradeHistory t) {
        String name = TradingConstants.findStock(t.getStockCode())
                .map(s -> s.name())
                .orElse(t.getStockCode());
        return new TradeHistoryDto(
                t.getId(),
                t.getStockCode(),
                name,
                t.getOrderType(),
                t.getPriceType(),
                t.getQuantity(),
                t.getPrice(),
                t.getFee(),
                t.getTax(),
                t.getTotalAmount(),
                t.getTradedAt(),
                t.getOrderId(),
                t.getRealizedPnl()
        );
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }
}
