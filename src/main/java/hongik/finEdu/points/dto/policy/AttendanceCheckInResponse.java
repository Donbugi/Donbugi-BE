package hongik.finEdu.points.dto.policy;

public record AttendanceCheckInResponse(
        int currentStreakDays,
        int pointsAwardedThisRequest,
        boolean alreadyCheckedInForDate,
        int balanceAfter
) {
}
