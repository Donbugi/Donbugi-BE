package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

public record OrderPreviewResponse(
        BigDecimal estAmount,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal totalPayment,
        BigDecimal priceUsed
) {
}
