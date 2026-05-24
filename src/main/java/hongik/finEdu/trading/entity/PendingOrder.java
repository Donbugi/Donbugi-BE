package hongik.finEdu.trading.entity;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "pending_order",
        indexes = {
                @Index(name = "idx_pending_order_user", columnList = "user_id"),
                @Index(name = "idx_pending_order_status", columnList = "status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 4)
    private OrderType orderType;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "target_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal targetPrice;

    /** 매수 지정가 선차감 금액 (체결 후 차액 환불) */
    @Column(name = "reserved_amount", precision = 15, scale = 2)
    private BigDecimal reservedAmount;

    /** 매도 지정가 취소 시 평균단가 복원용 */
    @Column(name = "sell_avg_price", precision = 10, scale = 2)
    private BigDecimal sellAvgPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PendingOrder(String userId, String stockCode, OrderType orderType, int quantity,
                        BigDecimal targetPrice, BigDecimal reservedAmount, BigDecimal sellAvgPrice) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.quantity = quantity;
        this.targetPrice = targetPrice;
        this.reservedAmount = reservedAmount;
        this.sellAvgPrice = sellAvgPrice;
        this.status = OrderStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public void markDone() {
        this.status = OrderStatus.DONE;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = OrderStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }
}
