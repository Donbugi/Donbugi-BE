package hongik.finEdu.trading.engine;

import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;

import java.math.BigDecimal;

public record PlaceOrderCommand(
        String userId,
        String stockCode,
        OrderType orderType,
        PriceType priceType,
        int quantity,
        BigDecimal limitPrice,
        String clientOrderId,
        Long originalOrderId
) {
}
