package hongik.finEdu.trading.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 장 마감 후 사용자별 총자산 스냅샷 (수익률 추이·랭킹용) */
@Entity
@Table(
        name = "trading_daily_snapshot",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_snapshot_user_date", columnNames = {"user_id", "snapshot_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyAssetSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal cash;

    @Column(name = "stock_evaluation", nullable = false, precision = 15, scale = 2)
    private BigDecimal stockEvaluation;

    @Column(name = "total_asset", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAsset;

    @Column(name = "realized_pnl", nullable = false, precision = 15, scale = 2)
    private BigDecimal realizedPnl;

    @Column(name = "profit_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal profitRate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public DailyAssetSnapshot(String userId, LocalDate snapshotDate, BigDecimal cash, BigDecimal stockEvaluation,
                              BigDecimal totalAsset, BigDecimal realizedPnl, BigDecimal profitRate) {
        this.userId = userId;
        this.snapshotDate = snapshotDate;
        this.cash = cash;
        this.stockEvaluation = stockEvaluation;
        this.totalAsset = totalAsset;
        this.realizedPnl = realizedPnl;
        this.profitRate = profitRate;
        this.createdAt = LocalDateTime.now();
    }
}
