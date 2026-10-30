package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

/**
 * 주문 미리보기.
 * 시장가는 현재 호가를 소진했을 때의 예상 체결금액(estAmount)·가중평균가(priceUsed).
 */
public record OrderPreviewResponse(
        BigDecimal estAmount,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal totalPayment,
        BigDecimal priceUsed,
        BigDecimal tickSize,
        BigDecimal upperLimit,
        BigDecimal lowerLimit,
        String session,
        BigDecimal availableCash,
        int sellableQuantity,
        long estimatedFillQuantity,
        boolean syntheticOrderBook
) {
}
