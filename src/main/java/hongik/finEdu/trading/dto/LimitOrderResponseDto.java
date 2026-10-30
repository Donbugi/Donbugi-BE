package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;

/** 지정가 주문 결과. 즉시 체결 가능한 만큼은 접수와 동시에 체결된다(filledQuantity). */
public record LimitOrderResponseDto(
        Long orderId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal targetPrice,
        OrderStatus status,
        int filledQuantity,
        BigDecimal avgFillPrice,
        boolean replayed
) {}
