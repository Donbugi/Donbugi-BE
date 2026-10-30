package hongik.finEdu.trading.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.trading.domain.FixedStock;
import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.dto.LimitOrderRequest;
import hongik.finEdu.trading.dto.LimitOrderResponseDto;
import hongik.finEdu.trading.dto.MarketOrderRequest;
import hongik.finEdu.trading.dto.MarketOrderResponseDto;
import hongik.finEdu.trading.dto.OrderPreviewRequest;
import hongik.finEdu.trading.dto.OrderPreviewResponse;
import hongik.finEdu.trading.dto.PendingOrderDto;
import hongik.finEdu.trading.engine.ExecutionReport;
import hongik.finEdu.trading.engine.OrderExecutionService;
import hongik.finEdu.trading.engine.PlaceOrderCommand;
import hongik.finEdu.trading.entity.TradingAccount;
import hongik.finEdu.trading.entity.TradingHolding;
import hongik.finEdu.trading.entity.TradingOrder;
import hongik.finEdu.trading.market.FeeTaxPolicy;
import hongik.finEdu.trading.market.Fill;
import hongik.finEdu.trading.market.MarketSession;
import hongik.finEdu.trading.market.MatchingRules;
import hongik.finEdu.trading.market.OrderBook;
import hongik.finEdu.trading.market.PriceLimitPolicy;
import hongik.finEdu.trading.market.TickSizePolicy;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import hongik.finEdu.trading.repository.TradingOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

/**
 * 주문 API 파사드 (요청 DTO ↔ 엔진 커맨드 변환).
 */
@Service
@RequiredArgsConstructor
public class TradingOrderService {

    private final OrderExecutionService executionService;
    private final OrderBookService orderBookService;
    private final StockPriceService stockPriceService;
    private final MarketClock marketClock;
    private final FeeTaxPolicy feeTaxPolicy;
    private final TradingOrderRepository orderRepository;
    private final TradingAccountRepository accountRepository;
    private final TradingHoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(String userId, OrderPreviewRequest request) {
        FixedStock stock = TradingConstants.requireStock(trim(request.stockCode()));
        if (request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1 이상이어야 합니다.");
        }
        if (request.orderType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "orderType은 필수입니다.");
        }
        var cq = stockPriceService.getCachedQuote(stock.code());
        PriceLimitPolicy.Band band = OrderExecutionService.bandOf(cq.quote());
        boolean buy = request.orderType() == OrderType.BUY;

        BigDecimal amount;
        BigDecimal priceUsed;
        long fillQty;
        OrderBook book = orderBookService.get(stock.code());
        if (request.limitPrice() != null && request.limitPrice().signum() > 0) {
            priceUsed = request.limitPrice();
            amount = priceUsed.multiply(BigDecimal.valueOf(request.quantity()));
            fillQty = request.quantity();
        } else {
            Fill fill = buy ? MatchingRules.marketBuy(book, request.quantity()) : MatchingRules.marketSell(book, request.quantity());
            amount = fill.amount();
            priceUsed = fill.averagePrice();
            fillQty = fill.quantity();
        }
        BigDecimal fee = feeTaxPolicy.fee(amount);
        BigDecimal tax = buy ? BigDecimal.ZERO : feeTaxPolicy.sellTax(amount, stock.market(), marketClock.today());
        BigDecimal total = buy ? amount.add(fee) : amount.subtract(fee).subtract(tax);

        BigDecimal available = accountRepository.findByUserId(userId)
                .map(TradingAccount::getAvailableCash).orElse(TradingConstants.INITIAL_CASH);
        int sellable = holdingRepository.findByUserIdAndStockCode(userId, stock.code())
                .map(TradingHolding::getSellableQuantity).orElse(0);

        return new OrderPreviewResponse(amount, fee, tax, total, priceUsed,
                TickSizePolicy.tickSize(priceUsed.signum() > 0 ? priceUsed : cq.quote().currentPrice()),
                band.upper(), band.lower(), marketClock.session().name(),
                available, sellable, fillQty, book.synthetic());
    }

    public MarketOrderResponseDto placeMarketOrder(String userId, MarketOrderRequest request, String idempotencyKey) {
        requireOrderType(request.orderType());
        ExecutionReport r = executionService.placeOrder(new PlaceOrderCommand(
                userId, trim(request.stockCode()), request.orderType(), PriceType.MARKET,
                request.quantity(), null, blankToNull(idempotencyKey), null));
        TradingOrder o = r.order();
        BigDecimal total = r.replayed() ? o.getFilledAmount()
                : (o.getOrderType() == OrderType.BUY ? r.amountNow().add(r.feeNow())
                : r.amountNow().subtract(r.feeNow()).subtract(r.taxNow()));
        return new MarketOrderResponseDto(o.getId(), o.getStockCode(), o.getOrderType(),
                o.getFilledQuantity(), o.getAvgFillPrice(), r.feeNow(), r.taxNow(), total,
                o.getQuantity(), o.getStatus(), r.replayed());
    }

    public LimitOrderResponseDto placeLimitOrder(String userId, LimitOrderRequest request, String idempotencyKey) {
        requireOrderType(request.orderType());
        ExecutionReport r = executionService.placeOrder(new PlaceOrderCommand(
                userId, trim(request.stockCode()), request.orderType(), PriceType.LIMIT,
                request.quantity(), request.targetPrice(), blankToNull(idempotencyKey), null));
        return toLimitResponse(r);
    }

    public LimitOrderResponseDto modifyOrder(String userId, Long orderId, BigDecimal price, Integer quantity,
                                             String idempotencyKey) {
        if (price == null && quantity == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "limitPrice 또는 quantity 중 하나는 필요합니다.");
        }
        return toLimitResponse(executionService.modify(userId, orderId, price, quantity, blankToNull(idempotencyKey)));
    }

    public void cancelOrder(String userId, Long orderId) {
        executionService.cancel(userId, orderId);
    }

    @Transactional(readOnly = true)
    public List<PendingOrderDto> listPendingOrders(String userId) {
        return orderRepository.findByUserIdAndStatusInOrderByCreatedAtDesc(userId,
                        EnumSet.of(OrderStatus.PENDING, OrderStatus.PARTIALLY_FILLED))
                .stream().map(TradingOrderService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Page<PendingOrderDto> listOrders(String userId, int page, int size) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId,
                        PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)))
                .map(TradingOrderService::toDto);
    }

    public MarketSession currentSession() {
        return marketClock.session();
    }

    private static LimitOrderResponseDto toLimitResponse(ExecutionReport r) {
        TradingOrder o = r.order();
        return new LimitOrderResponseDto(o.getId(), o.getStockCode(), o.getOrderType(), o.getQuantity(),
                o.getLimitPrice(), o.getStatus(), o.getFilledQuantity(), o.getAvgFillPrice(), r.replayed());
    }

    static PendingOrderDto toDto(TradingOrder o) {
        String name = TradingConstants.findStock(o.getStockCode()).map(FixedStock::name).orElse(o.getStockCode());
        return new PendingOrderDto(o.getId(), o.getStockCode(), name, o.getOrderType(), o.getQuantity(),
                o.getLimitPrice(), o.getStatus(), o.getCreatedAt(), o.getPriceType(), o.getFilledQuantity(),
                o.getRemainingQuantity(), o.getAvgFillPrice(), o.getLockedAmount(),
                o.getSession() == null ? null : o.getSession().name(), o.getOriginalOrderId());
    }

    private static void requireOrderType(OrderType t) {
        if (t == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "orderType(BUY/SELL)은 필수입니다.");
        }
    }

    private static String trim(String s) {
        if (s == null || s.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "stockCode는 필수입니다.");
        }
        return s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
