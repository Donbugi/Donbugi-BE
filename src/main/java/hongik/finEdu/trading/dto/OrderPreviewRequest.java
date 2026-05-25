package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;

public record OrderPreviewRequest(
        String userId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal limitPrice
) {
}
