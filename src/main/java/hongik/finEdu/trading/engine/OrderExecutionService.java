package hongik.finEdu.trading.engine;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.trading.client.StockQuote;
import hongik.finEdu.trading.domain.FixedStock;
import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.entity.CashLedgerEntry;
import hongik.finEdu.trading.entity.TradeHistory;
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
import hongik.finEdu.trading.repository.CashLedgerRepository;
import hongik.finEdu.trading.repository.TradeHistoryRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import hongik.finEdu.trading.repository.TradingOrderRepository;
import hongik.finEdu.trading.service.MarketClock;
import hongik.finEdu.trading.service.OrderBookService;
import hongik.finEdu.trading.service.StockPriceService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * 주문 접수·체결·취소의 트랜잭션 경계.
 *
 * <h3>정합성 규칙</h3>
 * <ul>
 *   <li>잠금 순서 고정: 주문 → 계좌 → 보유종목 (SELECT ... FOR UPDATE). 데드락 방지</li>
 *   <li>엔진(스케줄러)이 호출하는 체결/취소는 주문 1건 = 트랜잭션 1개 (REQUIRES_NEW).
 *       한 주문의 실패가 다른 주문 체결을 롤백시키던 기존 버그 제거</li>
 *   <li>매수 지정가·동시호가 주문은 금액을 차감하지 않고 lock, 매도는 수량 lock</li>
 *   <li>모든 현금 변동은 원장(trading_cash_ledger)에 기록 → 장 마감 후 정합성 검증</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private static final int MAX_ORDER_QUANTITY = 1_000_000;

    private final TradingOrderRepository orderRepository;
    private final TradingHoldingRepository holdingRepository;
    private final TradeHistoryRepository tradeHistoryRepository;
    private final CashLedgerRepository ledgerRepository;
    private final TradingAccountProvisioner accountProvisioner;
    private final FeeTaxPolicy feeTaxPolicy;
    private final MarketClock marketClock;
    private final StockPriceService stockPriceService;
    private final OrderBookService orderBookService;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    /** 시장가 주문 허용 최대 시세 지연 */
    @Value("${mock-trading.max-quote-age-seconds:90}")
    private long maxQuoteAgeSeconds;

    // ───────────────────────── 주문 접수 ─────────────────────────

    @Transactional
    public ExecutionReport placeOrder(PlaceOrderCommand cmd) {
        FixedStock stock = TradingConstants.requireStock(cmd.stockCode());
        validateBasics(cmd);

        if (cmd.clientOrderId() != null) {
            var existing = orderRepository.findByUserIdAndClientOrderId(cmd.userId(), cmd.clientOrderId());
            if (existing.isPresent()) {
                return ExecutionReport.replay(existing.get());
            }
        }

        MarketSession session = marketClock.session();
        if (!session.acceptsOrders()) {
            throw new BusinessException(ErrorCode.MARKET_CLOSED);
        }

        StockPriceService.CachedQuote cq = stockPriceService.getCachedQuote(stock.code());
        PriceLimitPolicy.Band band = bandOf(cq.quote());
        if (cmd.priceType() == PriceType.LIMIT) {
            validateLimitPrice(cmd.limitPrice(), band);
        }

        boolean marketNow = cmd.priceType() == PriceType.MARKET && session == MarketSession.REGULAR;
        OrderBook book = null;
        if (marketNow) {
            book = orderBookService.get(stock.code());
            if (book.synthetic() && stockPriceService.isStale(cq, Duration.ofSeconds(maxQuoteAgeSeconds))) {
                throw new BusinessException(ErrorCode.STALE_PRICE);
            }
        }

        // 잠금 순서: 계좌 → 보유종목
        TradingAccount account = accountProvisioner.lockOrCreate(cmd.userId());
        TradingHolding holding = null;
        if (cmd.orderType() == OrderType.SELL) {
            holding = holdingRepository.findForUpdate(cmd.userId(), stock.code())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY, "보유하지 않은 종목입니다."));
            if (holding.getSellableQuantity() < cmd.quantity()) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY,
                        "매도가능 " + holding.getSellableQuantity() + "주, 요청 " + cmd.quantity() + "주");
            }
        }

        TradingOrder order = orderRepository.save(TradingOrder.builder()
                .userId(cmd.userId())
                .stockCode(stock.code())
                .orderType(cmd.orderType())
                .priceType(cmd.priceType())
                .limitPrice(cmd.priceType() == PriceType.LIMIT ? cmd.limitPrice() : null)
                .quantity(cmd.quantity())
                .session(session)
                .clientOrderId(cmd.clientOrderId())
                .originalOrderId(cmd.originalOrderId())
                .build());
        meterRegistry.counter("trading.orders.placed",
                "side", cmd.orderType().name(), "type", cmd.priceType().name(), "session", session.name()).increment();

        if (marketNow) {
            return executeMarketNow(order, account, holding, book);
        }

        // 지정가(모든 세션) 또는 동시호가 시장가 → 자금/수량 lock 후 대기
        if (cmd.orderType() == OrderType.BUY) {
            BigDecimal lockPrice = cmd.priceType() == PriceType.LIMIT ? cmd.limitPrice() : band.upper();
            BigDecimal gross = lockPrice.multiply(BigDecimal.valueOf(cmd.quantity()));
            BigDecimal lockAmount = gross.add(feeTaxPolicy.fee(gross));
            account.lock(lockAmount);
            order.addLocked(lockAmount);
        } else {
            holding.lock(cmd.quantity());
        }

        // 접속매매 시간의 지정가는 즉시 체결 가능한 만큼 바로 체결 (marketable limit)
        if (session == MarketSession.REGULAR) {
            OrderBook live = orderBookService.get(stock.code());
            Fill fill = cmd.orderType() == OrderType.BUY
                    ? MatchingRules.limitBuy(live, cmd.limitPrice(), cmd.quantity(), null)
                    : MatchingRules.limitSell(live, cmd.limitPrice(), cmd.quantity(), null);
            if (!fill.isEmpty()) {
                return applyFill(order, account, holding, fill, true);
            }
        }
        return new ExecutionReport(order, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    /** 접속매매 시장가: 호가를 소진하며 즉시 체결, 잔량은 취소(IOC) */
    private ExecutionReport executeMarketNow(TradingOrder order, TradingAccount account,
                                             TradingHolding holding, OrderBook book) {
        Fill fill = order.getOrderType() == OrderType.BUY
                ? MatchingRules.marketBuy(book, order.getQuantity())
                : MatchingRules.marketSell(book, order.getQuantity());
        if (fill.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_LIQUIDITY);
        }
        if (order.getOrderType() == OrderType.BUY) {
            BigDecimal cost = fill.amount().add(feeTaxPolicy.fee(fill.amount()));
            if (account.getAvailableCash().compareTo(cost) < 0) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                        "주문가능금액 " + account.getAvailableCash().toPlainString() + "원, 필요 " + cost.toPlainString() + "원");
            }
        }
        ExecutionReport report = applyFill(order, account, holding, fill, false);
        if (order.getStatus().isOpen()) {
            order.cancel(); // IOC: 표시된 호가 잔량을 넘는 수량은 취소
        }
        return report;
    }

    // ───────────────────────── 엔진 호출 (주문 1건 = 트랜잭션 1개) ─────────────────────────

    /**
     * 접속매매 중 미체결 지정가 주문 체결 시도.
     * @return 이번에 체결된 내역 (호가 차감용). 체결 없으면 Fill.none()
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Fill matchLimitOrder(Long orderId, OrderBook book, StockPriceService.PriceRange range) {
        TradingOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.getStatus().isOpen() || order.getPriceType() != PriceType.LIMIT) {
            return Fill.none();
        }
        Fill fill = order.getOrderType() == OrderType.BUY
                ? MatchingRules.limitBuy(book, order.getLimitPrice(), order.getRemainingQuantity(), range.low())
                : MatchingRules.limitSell(book, order.getLimitPrice(), order.getRemainingQuantity(), range.high());
        if (fill.isEmpty()) {
            return fill;
        }
        TradingAccount account = accountProvisioner.lockOrCreate(order.getUserId());
        TradingHolding holding = order.getOrderType() == OrderType.SELL
                ? holdingRepository.findForUpdate(order.getUserId(), order.getStockCode()).orElse(null)
                : null;
        if (order.getOrderType() == OrderType.SELL && holding == null) {
            order.reject("보유 종목 없음");
            return Fill.none();
        }
        applyFill(order, account, holding, fill, true);
        return fill;
    }

    /** 동시호가 단일가 체결 (09:00 시가 / 15:30 종가) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fillAtAuction(Long orderId, BigDecimal auctionPrice) {
        TradingOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.getStatus().isOpen()) {
            return;
        }
        BigDecimal limit = order.getPriceType() == PriceType.LIMIT ? order.getLimitPrice() : null;
        Fill fill = MatchingRules.auction(order.getOrderType() == OrderType.BUY, limit,
                order.getRemainingQuantity(), auctionPrice);
        if (fill.isEmpty()) {
            return;
        }
        TradingAccount account = accountProvisioner.lockOrCreate(order.getUserId());
        TradingHolding holding = order.getOrderType() == OrderType.SELL
                ? holdingRepository.findForUpdate(order.getUserId(), order.getStockCode()).orElse(null)
                : null;
        if (order.getOrderType() == OrderType.SELL && holding == null) {
            order.reject("보유 종목 없음");
            return;
        }
        applyFill(order, account, holding, fill, true);
    }

    /** 사용자 취소 */
    @Transactional
    public TradingOrder cancel(String userId, Long orderId) {
        TradingOrder order = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getStatus().isOpen()) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_DONE);
        }
        releaseAndCancel(order);
        return order;
    }

    /** 장 마감 일괄 취소 (엔진) */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expire(Long orderId) {
        TradingOrder order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || !order.getStatus().isOpen()) {
            return;
        }
        releaseAndCancel(order);
    }

    /**
     * 정정: 원주문 잔량 취소 + 새 가격·수량으로 재주문을 한 트랜잭션에서 처리.
     * 이미 일부 체결된 수량은 유지된다.
     */
    @Transactional
    public ExecutionReport modify(String userId, Long orderId, BigDecimal newPrice, Integer newQuantity, String clientOrderId) {
        TradingOrder original = orderRepository.findByIdForUpdate(orderId)
                .filter(o -> o.getUserId().equals(userId))
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!original.getStatus().isOpen() || original.getPriceType() != PriceType.LIMIT) {
            throw new BusinessException(ErrorCode.ORDER_NOT_MODIFIABLE, "미체결 지정가 주문만 정정할 수 있습니다.");
        }
        int qty = newQuantity != null ? newQuantity : original.getRemainingQuantity();
        BigDecimal price = newPrice != null ? newPrice : original.getLimitPrice();
        releaseAndCancel(original);
        return placeOrder(new PlaceOrderCommand(userId, original.getStockCode(), original.getOrderType(),
                PriceType.LIMIT, qty, price, clientOrderId, original.getId()));
    }

    // ───────────────────────── 내부 ─────────────────────────

    private void releaseAndCancel(TradingOrder order) {
        if (order.getOrderType() == OrderType.BUY) {
            TradingAccount account = accountProvisioner.lockOrCreate(order.getUserId());
            account.unlock(order.releaseAllLocked());
        } else {
            holdingRepository.findForUpdate(order.getUserId(), order.getStockCode())
                    .ifPresent(h -> h.unlock(order.getRemainingQuantity()));
        }
        order.cancel();
        meterRegistry.counter("trading.orders.cancelled").increment();
    }

    /**
     * 체결 반영 (현금·보유·주문·체결내역·원장).
     * @param fromLock 매수: lock 된 금액에서 정산 / 매도: lock 된 수량에서 차감
     */
    private ExecutionReport applyFill(TradingOrder order, TradingAccount account, TradingHolding holding,
                                      Fill fill, boolean fromLock) {
        int qty = Math.toIntExact(fill.quantity());
        BigDecimal amount = fill.amount();
        BigDecimal fee = feeTaxPolicy.fee(amount);
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal realized = null;
        BigDecimal cashDelta;

        if (order.getOrderType() == OrderType.BUY) {
            BigDecimal cost = amount.add(fee);
            if (fromLock) {
                boolean completes = qty == order.getRemainingQuantity();
                BigDecimal released = completes ? order.releaseAllLocked() : order.releaseLocked(cost);
                account.unlock(released);
            }
            account.debit(cost);
            cashDelta = cost.negate();
            TradingHolding h = holdingRepository.findForUpdate(order.getUserId(), order.getStockCode()).orElse(null);
            if (h == null) {
                holdingRepository.save(TradingHolding.builder()
                        .userId(order.getUserId())
                        .stockCode(order.getStockCode())
                        .quantity(qty)
                        .avgPrice(amount.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP))
                        .build());
            } else {
                h.addBought(qty, amount);
            }
        } else {
            FixedStock stock = TradingConstants.requireStock(order.getStockCode());
            tax = feeTaxPolicy.sellTax(amount, stock.market(), marketClock.today());
            BigDecimal net = amount.subtract(fee).subtract(tax);
            BigDecimal costBasis = holding.getAvgPrice().multiply(BigDecimal.valueOf(qty));
            realized = amount.subtract(costBasis).subtract(fee).subtract(tax).setScale(2, RoundingMode.HALF_UP);
            holding.removeSold(qty, fromLock);
            if (holding.isEmpty()) {
                holdingRepository.delete(holding);
            }
            account.credit(net);
            cashDelta = net;
        }

        order.applyFill(qty, amount);

        TradeHistory trade = tradeHistoryRepository.save(TradeHistory.builder()
                .userId(order.getUserId())
                .stockCode(order.getStockCode())
                .orderType(order.getOrderType())
                .priceType(order.getPriceType())
                .quantity(qty)
                .price(fill.averagePrice())
                .fee(fee)
                .tax(tax)
                .totalAmount(cashDelta.abs())
                .orderId(order.getId())
                .realizedPnl(realized)
                .build());

        ledgerRepository.save(CashLedgerEntry.builder()
                .userId(order.getUserId())
                .kind(order.getOrderType() == OrderType.BUY ? CashLedgerEntry.Kind.BUY : CashLedgerEntry.Kind.SELL)
                .delta(cashDelta)
                .balanceAfter(account.getCash())
                .tradeId(trade.getId())
                .build());

        meterRegistry.counter("trading.fills", "side", order.getOrderType().name()).increment();
        eventPublisher.publishEvent(new TradeExecutedEvent(order.getUserId(), order.getId(), order.getStockCode(),
                order.getOrderType(), qty, fill.averagePrice(), order.getStatus()));
        return new ExecutionReport(order, qty, amount, fee, tax, false);
    }

    private static void validateBasics(PlaceOrderCommand cmd) {
        if (cmd.userId() == null || cmd.userId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        if (cmd.orderType() == null || cmd.priceType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "orderType·priceType은 필수입니다.");
        }
        if (cmd.quantity() <= 0 || cmd.quantity() > MAX_ORDER_QUANTITY) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1 ~ " + MAX_ORDER_QUANTITY + " 사이여야 합니다.");
        }
        if (cmd.clientOrderId() != null && cmd.clientOrderId().length() > 64) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Idempotency-Key 는 64자 이내여야 합니다.");
        }
    }

    private static void validateLimitPrice(BigDecimal price, PriceLimitPolicy.Band band) {
        if (price == null || price.signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지정가는 0보다 커야 합니다.");
        }
        if (!TickSizePolicy.isValidTick(price)) {
            throw new BusinessException(ErrorCode.INVALID_TICK_SIZE,
                    price.toPlainString() + "원 (호가단위 " + TickSizePolicy.tickSize(price).toPlainString() + "원)");
        }
        if (!band.contains(price)) {
            throw new BusinessException(ErrorCode.PRICE_LIMIT_EXCEEDED,
                    "허용 범위 " + band.lower().toPlainString() + " ~ " + band.upper().toPlainString() + "원");
        }
    }

    /** 기준가(전일 종가) = 현재가 − 전일대비 */
    public static PriceLimitPolicy.Band bandOf(StockQuote q) {
        BigDecimal base = q.currentPrice().subtract(q.changePrice() == null ? BigDecimal.ZERO : signedChange(q));
        if (base.signum() <= 0) {
            base = q.currentPrice();
        }
        return PriceLimitPolicy.band(base);
    }

    /**
     * 네이버 응답의 전일대비는 부호 없이 오고 방향(RISING/FALLING 등)이 따로 올 수 있다.
     * 방향이 하락이면 음수로 보정한다.
     */
    private static BigDecimal signedChange(StockQuote q) {
        BigDecimal change = q.changePrice();
        String dir = q.direction() == null ? "" : q.direction().toUpperCase();
        boolean falling = dir.contains("FALL") || dir.contains("LOWER") || dir.contains("하락") || dir.contains("DOWN");
        if (falling && change.signum() > 0) {
            return change.negate();
        }
        if (q.changeRate() != null && q.changeRate().signum() < 0 && change.signum() > 0) {
            return change.negate();
        }
        return change;
    }
}
