package hongik.finEdu.trading.entity;

import hongik.finEdu.trading.domain.OrderType;
import hongik.finEdu.trading.domain.PriceType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "trade_history",
        indexes = {
                @Index(name = "idx_trade_history_user", columnList = "user_id"),
                @Index(name = "idx_trade_history_stock", columnList = "stock_code"),
                @Index(name = "idx_trade_history_traded_at", columnList = "traded_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TradeHistory {

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

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal fee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "traded_at", nullable = false)
    private LocalDateTime tradedAt;

    @Builder
    public TradeHistory(String userId, String stockCode, OrderType orderType, PriceType priceType,
                        int quantity, BigDecimal price, BigDecimal fee, BigDecimal tax,
                        BigDecimal totalAmount) {
        this.userId = userId;
        this.stockCode = stockCode;
        this.orderType = orderType;
        this.priceType = priceType;
        this.quantity = quantity;
        this.price = price;
        this.fee = fee;
        this.tax = tax != null ? tax : BigDecimal.ZERO;
        this.totalAmount = totalAmount;
        this.tradedAt = LocalDateTime.now();
    }
}
