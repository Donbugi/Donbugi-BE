package hongik.finEdu.main.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_notification",
        indexes = @Index(name = "idx_notification_user_created", columnList = "user_id,created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 16)
    private String icon;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Builder
    public UserNotification(String userId, String icon, String title, String description) {
        this.userId = userId;
        this.icon = icon;
        this.title = title;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public void markRead() {
        this.readAt = LocalDateTime.now();
    }

    public boolean isUnread() {
        return readAt == null;
    }
}
