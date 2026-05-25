package hongik.finEdu.points.dto.policy;

public record AttendanceStatusResponse(
        int currentStreakDays,
        boolean checkedInToday,
        int balance
) {
}
