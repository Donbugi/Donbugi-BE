package hongik.finEdu.trading.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.trading.client.StockQuote;
import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.dto.*;
import hongik.finEdu.trading.entity.PendingOrder;
import hongik.finEdu.trading.entity.TradingHolding;
import hongik.finEdu.trading.repository.PendingOrderRepository;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import hongik.finEdu.trading.repository.TradingHoldingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class TradingOrderService {

    private final TradingExecutionService executionService;
    private final StockPriceService stockPriceService;
    private final MarketHoursService marketHoursService;
    private final PendingOrderRepository pendingOrderRepository;
    private final TradingAccountRepository accountRepository;
    private final TradingHoldingRepository holdingRepository;

    @Transactional(readOnly = true)
    public OrderPreviewResponse previewOrder(OrderPreviewRequest request) {
        String userId = requireUserId(request.userId());
        String stockCode = requireStockCode(request.stockCode());
        TradingConstants.requireStock(stockCode);
        if (request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1 이상이어야 합니다.");
        }
        BigDecimal price = request.limitPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            StockQuote quote = stockPriceService.getQuote(stockCode);
            price = quote.currentPrice();
        }
        BigDecimal amount = price.multiply(BigDecimal.valueOf(request.quantity()));
        return switch (request.orderType()) {
            case BUY -> {
                BigDecimal fee = TradingConstants.calcBuyFee(amount);
                yield new OrderPreviewResponse(amount, fee, BigDecimal.ZERO, amount.add(fee), price);
            }
            case SELL -> {
                BigDecimal fee = TradingConstants.calcSellFee(amount);
                BigDecimal tax = TradingConstants.calcSellTax(amount);
                yield new OrderPreviewResponse(amount, fee, tax, amount.subtract(fee).subtract(tax), price);
            }
        };
    }

    @Transactional
    public MarketOrderResponseDto placeMarketOrder(MarketOrderRequest request) {
        String userId = requireUserId(request.userId());
        String stockCode = requireStockCode(request.stockCode());
        TradingConstants.requireStock(stockCode);

        if (!marketHoursService.isMarketOpen()) {
            throw new BusinessException(ErrorCode.MARKET_CLOSED);
        }

        StockQuote quote = stockPriceService.getQuote(stockCode);
        BigDecimal price = quote.currentPrice();

        return switch (request.orderType()) {
            case BUY -> executionService.executeMarketBuy(userId, stockCode, request.quantity(), price);
            case SELL -> executionService.executeMarketSell(userId, stockCode, request.quantity(), price);
        };
    }

    @Transactional
    public LimitOrderResponseDto placeLimitOrder(LimitOrderRequest request) {
        String userId = requireUserId(request.userId());
        String stockCode = requireStockCode(request.stockCode());
        TradingConstants.requireStock(stockCode);

        if (request.quantity() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수량은 1 이상이어야 합니다.");
        }
        if (request.targetPrice() == null || request.targetPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지정가는 0보다 커야 합니다.");
        }

        StockQuote quote = stockPriceService.getQuote(stockCode);
        if (quote.currentPrice().compareTo(request.targetPrice()) == 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT_PRICE);
        }

        return switch (request.orderType()) {
            case BUY -> placeBuyLimit(userId, stockCode, request.quantity(), request.targetPrice());
            case SELL -> placeSellLimit(userId, stockCode, request.quantity(), request.targetPrice());
        };
    }

    @Transactional
    public void cancelOrder(String userId, Long orderId) {
        String uid = requireUserId(userId);
        PendingOrder order = pendingOrderRepository.findByIdAndUserId(orderId, uid)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_DONE);
        }
        executionService.cancelPendingOrder(order);
    }

    @Transactional(readOnly = true)
    public java.util.List<PendingOrderDto> listPendingOrders(String userId) {
        String uid = requireUserId(userId);
        return pendingOrderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(uid, OrderStatus.PENDING)
                .stream()
                .map(this::toPendingDto)
                .toList();
    }

    private LimitOrderResponseDto placeBuyLimit(String userId, String stockCode, int quantity, BigDecimal targetPrice) {
        BigDecimal amount = targetPrice.multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = TradingConstants.calcBuyFee(amount);
        BigDecimal reserved = amount.add(fee);

        var account = executionService.getOrCreateAccount(userId);
        if (account.getCash().compareTo(reserved) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                    "필요 " + reserved + "원, 보유 " + account.getCash() + "원");
        }
        account.debit(reserved);
        accountRepository.save(account);

        PendingOrder order = pendingOrderRepository.save(PendingOrder.builder()
                .userId(userId)
                .stockCode(stockCode)
                .orderType(OrderType.BUY)
                .quantity(quantity)
                .targetPrice(targetPrice)
                .reservedAmount(reserved)
                .sellAvgPrice(null)
                .build());

        return new LimitOrderResponseDto(
                order.getId(), stockCode, OrderType.BUY, quantity, targetPrice, OrderStatus.PENDING);
    }

    private LimitOrderResponseDto placeSellLimit(String userId, String stockCode, int quantity, BigDecimal targetPrice) {
        TradingHolding holding = holdingRepository.findByUserIdAndStockCode(userId, stockCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY));
        if (holding.getQuantity() < quantity) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY);
        }
        holding.reduceQuantity(quantity);
        if (holding.getQuantity() == 0) {
            holdingRepository.delete(holding);
        } else {
            holdingRepository.save(holding);
        }

        PendingOrder order = pendingOrderRepository.save(PendingOrder.builder()
                .userId(userId)
                .stockCode(stockCode)
                .orderType(OrderType.SELL)
                .quantity(quantity)
                .targetPrice(targetPrice)
                .reservedAmount(null)
                .sellAvgPrice(holding.getAvgPrice())
                .build());

        return new LimitOrderResponseDto(
                order.getId(), stockCode, OrderType.SELL, quantity, targetPrice, OrderStatus.PENDING);
    }

    private PendingOrderDto toPendingDto(PendingOrder order) {
        String name = TradingConstants.findStock(order.getStockCode())
                .map(s -> s.name())
                .orElse(order.getStockCode());
        return new PendingOrderDto(
                order.getId(),
                order.getStockCode(),
                name,
                order.getOrderType(),
                order.getQuantity(),
                order.getTargetPrice(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }

    private static String requireStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "stockCode는 필수입니다.");
        }
        return stockCode.trim();
    }
}
