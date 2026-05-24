package hongik.finEdu.trading.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

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

    @Column(name = "avg_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal avgPrice;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public TradingHolding(String userId, String stockCode, int quantity, BigDecimal avgPrice) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.quantity = quantity;
        this.avgPrice = avgPrice;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void addQuantity(int qty, BigDecimal price) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다.");
        }
        BigDecimal totalCost = avgPrice.multiply(BigDecimal.valueOf(quantity))
                .add(price.multiply(BigDecimal.valueOf(qty)));
        this.quantity += qty;
        this.avgPrice = totalCost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);
        this.updatedAt = LocalDateTime.now();
    }

    public void reduceQuantity(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다.");
        }
        if (this.quantity < qty) {
            throw new IllegalStateException("보유 수량 부족");
        }
        this.quantity -= qty;
        this.updatedAt = LocalDateTime.now();
    }

    /** 매도 지정가 취소 시 평균단가 유지하며 수량 복원 */
    public void restoreQuantity(int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 양수여야 합니다.");
        }
        this.quantity += qty;
        this.updatedAt = LocalDateTime.now();
    }
}
