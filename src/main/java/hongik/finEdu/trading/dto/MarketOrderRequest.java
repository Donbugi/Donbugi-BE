package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderType;

public record MarketOrderRequest(
        String userId,
        String stockCode,
        OrderType orderType,
        int quantity
) {}
