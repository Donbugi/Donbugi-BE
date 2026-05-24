package hongik.finEdu.trading.client;

import java.math.BigDecimal;

public record StockQuote(
        String stockCode,
        String stockName,
        BigDecimal currentPrice,
        BigDecimal changeRate,
        BigDecimal changePrice,
        String direction,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        Long volume,
        String marketStatus
) {}
