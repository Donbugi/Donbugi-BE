package hongik.finEdu.points.dto;

import java.time.YearMonth;

public record PointMonthlySummaryResponse(
        String userId,
        YearMonth month,
        int earnedPoints,
        int spentPoints,
        int netPoints
) {
}
