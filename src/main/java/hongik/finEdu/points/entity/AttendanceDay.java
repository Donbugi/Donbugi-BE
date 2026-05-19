package hongik.finEdu.points.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "attendance_day",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_user_date", columnNames = {"user_id", "attendance_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AttendanceDay(String userId, LocalDate attendanceDate) {
        this.userId = userId;
        this.attendanceDate = attendanceDate;
        this.createdAt = LocalDateTime.now();
    }
}
