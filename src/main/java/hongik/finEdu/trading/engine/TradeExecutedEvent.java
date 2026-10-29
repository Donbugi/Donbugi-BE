package hongik.finEdu.trading.engine;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;

/** 체결 발생 이벤트 (커밋 후 SSE 로 사용자에게 푸시) */
public record TradeExecutedEvent(
        String userId,
        Long orderId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal avgPrice,
        OrderStatus orderStatus
) {
}
