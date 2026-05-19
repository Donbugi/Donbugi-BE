package hongik.finEdu.points.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 포인트 1회 지급의 멱등성 보장 (같은 claimKey 로는 한 번만 지급).
 */
@Entity
@Table(
        name = "point_reward_claim",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_claim_key", columnNames = "claim_key")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointRewardClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "claim_key", nullable = false, length = 220)
    private String claimKey;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PointRewardClaim(String userId, String claimKey, int points, String title) {
        this.userId = userId;
        this.claimKey = claimKey;
        this.points = points;
        this.title = title;
        this.createdAt = LocalDateTime.now();
    }
}
