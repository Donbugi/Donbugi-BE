package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

public record RankingEntryDto(int rank, String nickname, BigDecimal profitRate, boolean me) {
}
