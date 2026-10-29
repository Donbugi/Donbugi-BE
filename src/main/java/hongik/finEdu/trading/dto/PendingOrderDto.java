package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PendingOrderDto(
        Long orderId,
        String stockCode,
        String stockName,
        OrderType orderType,
        int quantity,
        BigDecimal targetPrice,
        OrderStatus status,
        LocalDateTime createdAt,
        PriceType priceType,
        int filledQuantity,
        int remainingQuantity,
        BigDecimal avgFillPrice,
        BigDecimal lockedAmount,
        String session,
        Long originalOrderId
) {}
