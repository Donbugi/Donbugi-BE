package hongik.finEdu.trading.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 예수금 변동 원장 (append-only). 정합성 검증: account.cash == Σ delta
 */
@Entity
@Table(
        name = "trading_cash_ledger",
        indexes = @Index(name = "idx_cash_ledger_user_created", columnList = "user_id,created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashLedgerEntry {

    public enum Kind {
        /** 계좌 개설(초기 자금) 또는 원장 도입 시점의 기초 잔액 */
        OPENING,
        /** 매수 체결 (체결금액 + 수수료) */
        BUY,
        /** 매도 체결 (체결금액 − 수수료 − 세금) */
        SELL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Kind kind;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal delta;

    @Column(name = "balance_after", nullable = false, precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public CashLedgerEntry(String userId, Kind kind, BigDecimal delta, BigDecimal balanceAfter, Long tradeId) {
        this.userId = userId;
        this.kind = kind;
        this.delta = delta;
        this.balanceAfter = balanceAfter;
        this.tradeId = tradeId;
        this.createdAt = LocalDateTime.now();
    }
}
