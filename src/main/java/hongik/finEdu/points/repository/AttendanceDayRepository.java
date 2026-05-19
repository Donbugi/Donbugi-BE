package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, Long> {

    boolean existsByUserIdAndAttendanceDate(String userId, LocalDate attendanceDate);
}
