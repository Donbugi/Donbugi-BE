package hongik.finEdu.calendar.repository;

import hongik.finEdu.calendar.entity.UserCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface UserCalendarEventRepository extends JpaRepository<UserCalendarEvent, Long> {

    List<UserCalendarEvent> findByEventDateBetweenOrderByEventDateAscEventTimeAsc(
            LocalDate fromInclusive, LocalDate toInclusive);
}
