package hongik.finEdu.points.entity;

import hongik.finEdu.points.domain.PointBenefitCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Transactional Outbox: 포인트 차감과 같은 트랜잭션에서 "메일 보낼 일"을 기록하고, 발송은 커밋 후 비동기·재시도.
 * 기존에는 요청 스레드에서 SMTP 를 동기 호출해 응답이 느리고, 실패하면 포인트만 빠지고 안내가 유실됐다.
 */
@Entity
@Table(
        name = "point_mail_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_mail_outbox_ref", columnNames = "redemption_ref"),
        indexes = @Index(name = "idx_mail_outbox_status_next", columnList = "status,next_attempt_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointMailOutbox {

    public enum Status { PENDING, SENT, SKIPPED, FAILED }

    public static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "redemption_ref", nullable = false, length = 36)
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

    @Column(name = "balance_after", nullable = false)
    private int balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder
    public PointMailOutbox(String redemptionRef, String userId, String email, PointBenefitCode benefitCode,
                           int pointsSpent, int balanceAfter) {
        this.redemptionRef = redemptionRef;
        this.userId = userId;
        this.email = email;
        this.benefitCode = benefitCode;
        this.pointsSpent = pointsSpent;
        this.balanceAfter = balanceAfter;
        this.status = Status.PENDING;
        this.attempts = 0;
        this.createdAt = LocalDateTime.now();
        this.nextAttemptAt = this.createdAt;
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = LocalDateTime.now();
    }

    public void markSkipped() {
        this.status = Status.SKIPPED;
    }

    /** 지수 백오프(1, 2, 4, 8분) 후 재시도, 최대 시도 초과 시 FAILED */
    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(500, error.length()));
        if (attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;
        } else {
            this.nextAttemptAt = LocalDateTime.now().plusMinutes(1L << (attempts - 1));
        }
    }
}
