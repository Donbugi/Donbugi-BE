package hongik.finEdu.trading.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trading_account", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal cash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @Builder
    public TradingAccount(String userId, BigDecimal cash) {
        this.userId = userId;
        this.cash = cash;
        this.createdAt = LocalDateTime.now();
    }

    public void credit(BigDecimal amount) {
        this.cash = this.cash.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (this.cash.compareTo(amount) < 0) {
            throw new IllegalStateException("잔고 부족");
        }
        this.cash = this.cash.subtract(amount);
    }
}
