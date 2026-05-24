package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

public record HoldingDto(
        String stockCode,
        String stockName,
        int quantity,
        BigDecimal avgPrice,
        BigDecimal currentPrice,
        BigDecimal evaluationAmount,
        BigDecimal profit,
        BigDecimal profitRate
) {}
