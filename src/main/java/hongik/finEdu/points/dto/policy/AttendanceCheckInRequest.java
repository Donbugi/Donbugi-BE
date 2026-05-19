package hongik.finEdu.points.dto.policy;

import java.time.LocalDate;

public record AttendanceCheckInRequest(String userId, LocalDate date) {
}
