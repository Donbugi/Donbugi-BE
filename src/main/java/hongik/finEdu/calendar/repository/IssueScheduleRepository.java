package hongik.finEdu.calendar.repository;

import hongik.finEdu.calendar.entity.IssueSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueScheduleRepository extends JpaRepository<IssueSchedule, Long> {

    List<IssueSchedule> findByYearAndMonthOrderByDayAscIdAsc(int year, int month);
}
