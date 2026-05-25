package hongik.finEdu.trading.dto;

import java.util.List;

public record AccountSnapshotDto(
        AccountSummaryDto summary,
        List<HoldingDto> holdings,
        List<StockSummaryDto> quotes,
        boolean marketOpen
) {
}
