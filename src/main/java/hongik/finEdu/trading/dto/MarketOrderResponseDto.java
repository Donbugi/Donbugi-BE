package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;

import java.math.BigDecimal;

/**
 * 시장가 주문 결과.
 * quantity/price 는 <b>실제 체결 수량/가중평균 체결가</b>. 호가 잔량 부족 시 requestedQuantity 보다 적을 수 있다(잔량 취소).
 * 동시호가 시간에 접수된 시장가는 status=PENDING, quantity=0 (09:00/15:30 단일가 체결).
 */
public record MarketOrderResponseDto(
        Long orderId,
        String stockCode,
        OrderType orderType,
        int quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal totalAmount,
        int requestedQuantity,
        OrderStatus status,
        boolean replayed
) {}
