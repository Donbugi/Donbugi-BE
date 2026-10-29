package hongik.finEdu.trading.dto;

import java.time.LocalDate;
import java.util.List;

public record RankingResponse(LocalDate date, List<RankingEntryDto> top, RankingEntryDto myRank) {
}
