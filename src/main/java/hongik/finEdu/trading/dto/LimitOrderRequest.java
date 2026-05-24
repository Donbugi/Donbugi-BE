package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;

public record LimitOrderRequest(
        String userId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal targetPrice
) {}
