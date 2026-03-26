package hongik.finEdu.calendar.repository;

import hongik.finEdu.calendar.entity.TaxSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxScheduleRepository extends JpaRepository<TaxSchedule, Long> {

    // 중복 체크용 - 날짜 + 제목으로 조회
    Optional<TaxSchedule> findByScheduleDateAndTitle(LocalDate scheduleDate, String title);

    // 특정 연월 전체 조회
    List<TaxSchedule> findByYearAndMonthOrderByDayAsc(int year, int month);

    // 특정 날짜 범위 조회 (캘린더 뷰용)
    List<TaxSchedule> findByScheduleDateBetweenOrderByScheduleDateAsc(
        LocalDate from, LocalDate to
    );

    // 특정 월 데이터 존재 여부 확인
    boolean existsByYearAndMonth(int year, int month);

    // 오래된 데이터 삭제 (6개월 이전)
    @Modifying
    @Query("DELETE FROM TaxSchedule t WHERE t.scheduleDate < :cutoffDate")
    int deleteOldSchedules(@Param("cutoffDate") LocalDate cutoffDate);
}
