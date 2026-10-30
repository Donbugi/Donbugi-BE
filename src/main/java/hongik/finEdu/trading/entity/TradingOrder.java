package hongik.finEdu.trading.entity;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;
import hongik.finEdu.trading.market.MarketSession;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 주문 (시장가·지정가, 부분체결 지원). 기존 pending_order 를 대체한다.
 */
@Entity
@Table(
        name = "trading_order",
        uniqueConstraints = @UniqueConstraint(name = "uk_trading_order_client_id", columnNames = {"user_id", "client_order_id"}),
        indexes = {
                @Index(name = "idx_trading_order_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_trading_order_status_stock", columnList = "status,stock_code")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradingOrder {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "price_type", nullable = false, length = 6)
    private PriceType priceType;

    /** 지정가 (시장가는 null) */
    @Column(name = "limit_price", precision = 12, scale = 2)
    private BigDecimal limitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "filled_quantity", nullable = false)
    private int filledQuantity;

    /** 누적 체결금액 (수수료·세금 제외) */
    @Column(name = "filled_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal filledAmount = BigDecimal.ZERO;

    /** 매수 주문에 아직 묶여 있는 금액 (잔량 × 지정가 + 예상 수수료) */
    @Column(name = "locked_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal lockedAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_session", nullable = false, length = 20)
    private MarketSession session;

    /** 멱등키 (Idempotency-Key 헤더). 같은 사용자·키로 재요청하면 기존 주문을 돌려준다 */
    @Column(name = "client_order_id", length = 64)
    private String clientOrderId;

    @Column(name = "reject_reason", length = 200)
    private String rejectReason;

    /** 정정 주문이면 원주문 id */
    @Column(name = "original_order_id")
    private Long originalOrderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Builder
    public TradingOrder(String userId, String stockCode, OrderType orderType, PriceType priceType,
                        BigDecimal limitPrice, int quantity, MarketSession session,
                        String clientOrderId, Long originalOrderId) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.priceType = priceType;
        this.limitPrice = limitPrice;
        this.quantity = quantity;
        this.filledQuantity = 0;
        this.filledAmount = BigDecimal.ZERO;
        this.lockedAmount = BigDecimal.ZERO;
        this.status = OrderStatus.PENDING;
        this.session = session;
        this.clientOrderId = clientOrderId;
        this.originalOrderId = originalOrderId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public int getRemainingQuantity() {
        return quantity - filledQuantity;
    }

    public BigDecimal getAvgFillPrice() {
        return filledQuantity == 0 ? null
                : filledAmount.divide(BigDecimal.valueOf(filledQuantity), 2, RoundingMode.HALF_UP);
    }

    public void addLocked(BigDecimal amount) {
        this.lockedAmount = lockedAmount.add(amount);
        touch();
    }

    /** 묶인 금액 일부 해제. 실제 해제한 금액 반환 */
    public BigDecimal releaseLocked(BigDecimal amount) {
        BigDecimal released = amount.min(lockedAmount).max(BigDecimal.ZERO);
        this.lockedAmount = lockedAmount.subtract(released);
        touch();
        return released;
    }

    public BigDecimal releaseAllLocked() {
        return releaseLocked(lockedAmount);
    }

    public void applyFill(int qty, BigDecimal amount) {
        if (qty <= 0 || qty > getRemainingQuantity()) {
            throw new IllegalStateException("체결 수량 오류: qty=" + qty + ", remaining=" + getRemainingQuantity());
        }
        this.filledQuantity += qty;
        this.filledAmount = filledAmount.add(amount);
        this.status = filledQuantity == quantity ? OrderStatus.FILLED : OrderStatus.PARTIALLY_FILLED;
        touch();
    }

    public void cancel() {
        this.status = OrderStatus.CANCELLED;
        touch();
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.rejectReason = reason == null ? null : reason.substring(0, Math.min(200, reason.length()));
        touch();
    }

    private void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
