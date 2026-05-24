package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LimitOrderResponseDto(
        Long orderId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal targetPrice,
        OrderStatus status
) {}
