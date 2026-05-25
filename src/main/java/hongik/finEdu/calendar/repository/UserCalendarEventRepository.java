package hongik.finEdu.calendar.repository;

import hongik.finEdu.calendar.entity.UserCalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserCalendarEventRepository extends JpaRepository<UserCalendarEvent, Long> {

    List<UserCalendarEvent> findByUserIdAndEventDateBetweenOrderByEventDateAscEventTimeAsc(
            String userId, LocalDate fromInclusive, LocalDate toInclusive);

    Optional<UserCalendarEvent> findByIdAndUserId(Long id, String userId);
}
