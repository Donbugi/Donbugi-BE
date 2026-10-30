package hongik.finEdu.trading.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetHistoryPointDto(
        LocalDate date,
        BigDecimal totalAsset,
        BigDecimal profitRate,
        BigDecimal realizedPnl
) {
}
