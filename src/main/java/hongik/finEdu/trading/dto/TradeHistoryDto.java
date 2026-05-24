package hongik.finEdu.trading.dto;

import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TradeHistoryDto(
        Long id,
        String stockCode,
        String stockName,
        OrderType orderType,
        PriceType priceType,
        int quantity,
        BigDecimal price,
        BigDecimal fee,
        BigDecimal tax,
        BigDecimal totalAmount,
        LocalDateTime tradedAt
) {}
