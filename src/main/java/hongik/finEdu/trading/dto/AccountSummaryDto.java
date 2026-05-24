package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

public record AccountSummaryDto(
        String userId,
        BigDecimal cash,
        BigDecimal totalEvaluation,
        BigDecimal totalAsset,
        BigDecimal totalProfit,
        BigDecimal totalProfitRate
) {}
