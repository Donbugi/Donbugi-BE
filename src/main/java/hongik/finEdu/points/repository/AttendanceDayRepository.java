package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.AttendanceDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface AttendanceDayRepository extends JpaRepository<AttendanceDay, Long> {

    boolean existsByUserIdAndAttendanceDate(String userId, LocalDate attendanceDate);

    /** 연속 출석 계산용: 기간 내 출석일 (최신순) — 하루 1쿼리씩 도는 N+1 제거 */
    @Query("""
            SELECT a.attendanceDate FROM AttendanceDay a
            WHERE a.userId = :userId AND a.attendanceDate BETWEEN :from AND :to
            ORDER BY a.attendanceDate DESC
            """)
    List<LocalDate> findDatesBetweenDesc(@Param("userId") String userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /** 동시 요청에도 안전한 출석 기록. 새로 기록했으면 1, 이미 있으면 0. */
    @Modifying
    @Query(value = """
            INSERT INTO attendance_day (user_id, attendance_date, created_at)
            VALUES (:userId, :date, :createdAt)
            ON CONFLICT (user_id, attendance_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId,
                       @Param("date") LocalDate date,
                       @Param("createdAt") LocalDateTime createdAt);
}
