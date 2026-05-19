package hongik.finEdu.points.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "point_history",
        indexes = @Index(name = "idx_point_hist_user_time", columnList = "user_id,occurred_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    /** 적립은 양수, 사용(교환)은 음수 */
    @Column(name = "delta", nullable = false)
    private int delta;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @Column(name = "related_ref", length = 36)
    private String relatedRef;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    public PointHistoryEntry(String userId, int delta, String title, String detail,
                             String relatedRef, LocalDateTime occurredAt) {
        this.userId = userId;
        this.delta = delta;
        this.title = title;
        this.detail = detail;
        this.relatedRef = relatedRef;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
    }
}
