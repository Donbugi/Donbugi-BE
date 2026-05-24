package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

public record StockDetailDto(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        Long volume,
        BigDecimal changeRate,
        BigDecimal changePrice,
        String direction,
        String marketStatus
) {}
