package hongik.finEdu.calendar.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "user_calendar_event",
        indexes = {
                @Index(name = "idx_user_cal_event_date", columnList = "event_date"),
                @Index(name = "idx_user_cal_user_date", columnList = "user_id,event_date")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCalendarEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 200)
    private String title;

    /** 일정 날짜 (년·월·일) */
    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    /** 24시간제 시각 (오전/오후·시·분은 응답 시 12시간제로 환산) */
    @Column(name = "event_time", nullable = false)
    private LocalTime eventTime;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public UserCalendarEvent(String userId, String title, LocalDate eventDate, LocalTime eventTime, String memo) {
        this.userId = userId;
        this.title = title;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.memo = memo;
        this.createdAt = LocalDateTime.now();
    }

    public void update(String title, LocalDate eventDate, LocalTime eventTime, String memo) {
        this.title = title;
        this.eventDate = eventDate;
        this.eventTime = eventTime;
        this.memo = memo;
    }
}
