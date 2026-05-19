package hongik.finEdu.points.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "point_account",
        uniqueConstraints = @UniqueConstraint(name = "uk_point_user", columnNames = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private int balance;

    @Version
    private Long version;

    @Builder
    public PointAccount(String userId, int balance) {
        this.userId = userId;
        this.balance = balance;
    }

    public void credit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("적립 포인트는 양수여야 합니다.");
        }
        this.balance += amount;
    }

    public void debit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("차감 포인트는 양수여야 합니다.");
        }
        if (this.balance < amount) {
            throw new IllegalStateException("잔액 부족");
        }
        this.balance -= amount;
    }
}
