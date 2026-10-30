package hongik.finEdu.trading.entity;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 보유 종목.
 * <ul>
 *   <li>quantity: 보유 수량 (미체결 매도 주문 수량 포함)</li>
 *   <li>lockedQuantity: 미체결 매도 주문에 묶인 수량</li>
 *   <li>매도가능수량 = quantity − lockedQuantity</li>
 * </ul>
 * 기존에는 지정가 매도 주문 즉시 수량을 차감(0이면 행 삭제)해서 미체결 중 보유 화면에서 종목이 사라지고,
 * 취소 시 평균단가를 주문에 따로 저장해 복원하는 우회 로직이 필요했다.
 */
@Entity
@Table(
        name = "trading_holding",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "stock_code"}),
        indexes = @Index(name = "idx_trading_holding_user", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "locked_quantity", nullable = false)
    private int lockedQuantity;

    /** 평균 매입단가 (이동평균법, 수수료 제외) */
    @Column(name = "avg_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Builder
    public TradingHolding(String userId, String stockCode, int quantity, BigDecimal avgPrice) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.quantity = quantity;
        this.lockedQuantity = 0;
        this.avgPrice = avgPrice.setScale(2, RoundingMode.HALF_UP);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public int getSellableQuantity() {
        return quantity - lockedQuantity;
    }

    /** 매수 체결 반영 (이동평균) — amount 는 체결금액 합계(수수료 제외) */
    public void addBought(int qty, BigDecimal amount) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상");
        }
        BigDecimal totalCost = avgPrice.multiply(BigDecimal.valueOf(quantity)).add(amount);
        this.quantity += qty;
        this.avgPrice = totalCost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        touch();
    }

    public void lock(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상");
        }
        if (getSellableQuantity() < qty) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY,
                    "매도가능 " + getSellableQuantity() + "주, 요청 " + qty + "주");
        }
        this.lockedQuantity += qty;
        touch();
    }

    public void unlock(int qty) {
        if (qty <= 0) {
            return;
        }
        this.lockedQuantity = Math.max(0, lockedQuantity - qty);
        touch();
    }

    /** 매도 체결 반영. fromLocked=true 면 미체결 매도(지정가·동시호가)로 묶어둔 수량에서 차감 */
    public void removeSold(int qty, boolean fromLocked) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상");
        }
        if (fromLocked) {
            if (lockedQuantity < qty) {
                throw new IllegalStateException("묶인 수량 부족: locked=" + lockedQuantity + ", qty=" + qty);
            }
            this.lockedQuantity -= qty;
        } else if (getSellableQuantity() < qty) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_QUANTITY,
                    "매도가능 " + getSellableQuantity() + "주, 요청 " + qty + "주");
        }
        this.quantity -= qty;
        touch();
    }

    public boolean isEmpty() {
        return quantity == 0 && lockedQuantity == 0;
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
