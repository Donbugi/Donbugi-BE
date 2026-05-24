package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketOrderResponseDto(
        Long orderId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal totalAmount
) {}
