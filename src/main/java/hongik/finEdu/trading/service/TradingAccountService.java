package hongik.finEdu.trading.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.trading.client.StockQuote;
import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.dto.AccountSnapshotDto;
import hongik.finEdu.trading.dto.AccountSummaryDto;
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
    private final MarketHoursService marketHoursService;
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
        BigDecimal cash = accountRepository.findByUserId(uid)
                .map(TradingAccount::getCash)
                .orElse(TradingConstants.INITIAL_CASH);
        List<HoldingDto> holdings = listHoldings(uid);

        BigDecimal stockEval = holdings.stream()
                .map(HoldingDto::evaluationAmount)
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
                profitRate
        );
    }

    @Transactional(readOnly = true)
    public AccountSnapshotDto getAccountSnapshot(String userId) {
        String uid = requireUserId(userId);
        return new AccountSnapshotDto(
                getAccountSummary(uid),
                listHoldings(uid),
                listStocks(),
                marketHoursService.isMarketOpen()
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
                profitRate
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
                t.getTradedAt()
        );
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }
}
