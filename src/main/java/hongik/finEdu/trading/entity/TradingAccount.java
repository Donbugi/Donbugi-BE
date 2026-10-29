package hongik.finEdu.trading.entity;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 모의투자 현금 계좌.
 * <ul>
 *   <li>cash: 예수금 총액 (미체결 매수 주문의 묶인 금액 포함)</li>
 *   <li>lockedCash: 미체결 매수 주문에 묶인 금액</li>
 *   <li>주문가능금액 = cash − lockedCash</li>
 * </ul>
 * 기존에는 지정가 매수 시 cash 에서 바로 차감해 "예수금이 줄었다가 취소 시 돌아오는" 비현실적 동작이었다.
 */
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

    @Column(name = "locked_cash", nullable = false, precision = 15, scale = 2)
    private BigDecimal lockedCash = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    @Builder
    public TradingAccount(String userId, BigDecimal cash) {
        this.userId = userId;
        this.cash = cash;
        this.lockedCash = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now();
    }

    public BigDecimal getAvailableCash() {
        return cash.subtract(lockedCash);
    }

    public void lock(BigDecimal amount) {
        requirePositive(amount);
        if (getAvailableCash().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                    "주문가능금액 " + getAvailableCash().toPlainString() + "원, 필요 " + amount.toPlainString() + "원");
        }
        this.lockedCash = lockedCash.add(amount);
    }

    public void unlock(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        // 반올림 오차 등으로 음수가 되지 않도록 방어
        this.lockedCash = lockedCash.subtract(amount).max(BigDecimal.ZERO);
    }

    public void credit(BigDecimal amount) {
        requirePositiveOrZero(amount);
        this.cash = cash.add(amount);
    }

    /** 예수금 차감. 묶인 금액을 침범하지 않는다 (먼저 unlock 후 호출). */
    public void debit(BigDecimal amount) {
        requirePositiveOrZero(amount);
        if (getAvailableCash().compareTo(amount) < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_CASH,
                    "주문가능금액 " + getAvailableCash().toPlainString() + "원, 필요 " + amount.toPlainString() + "원");
        }
        this.cash = cash.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("금액은 0보다 커야 합니다: " + amount);
        }
    }

    private static void requirePositiveOrZero(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다: " + amount);
        }
    }
}
