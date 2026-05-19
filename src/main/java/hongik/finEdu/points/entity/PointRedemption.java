package hongik.finEdu.points.entity;

import hongik.finEdu.points.domain.PointBenefitCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "point_redemption",
        indexes = {
                @Index(name = "idx_redemption_user", columnList = "user_id"),
                @Index(name = "idx_redemption_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 고객·CS 조회용 외부 참조 번호 */
    @Column(name = "redemption_ref", nullable = false, unique = true, length = 36)
    private String redemptionRef;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "benefit_code", nullable = false, length = 40)
    private PointBenefitCode benefitCode;

    @Column(name = "points_spent", nullable = false)
    private int pointsSpent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public PointRedemption(String redemptionRef, String userId, String email,
                           PointBenefitCode benefitCode, int pointsSpent) {
        this.redemptionRef = redemptionRef;
        this.userId = userId;
        this.email = email;
        this.benefitCode = benefitCode;
        this.pointsSpent = pointsSpent;
        this.createdAt = LocalDateTime.now();
    }
}
