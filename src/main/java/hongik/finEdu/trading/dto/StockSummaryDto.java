package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

public record StockSummaryDto(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal changeRate,
        BigDecimal changePrice,
        String direction
) {}
