package hongik.finEdu.trading.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * 매매 수수료·증권거래세.
 *
 * <ul>
 *   <li>수수료: 체결금액 × 수수료율, <b>원 미만 절사</b> (증권사 일반 관행. 기존 코드는 반올림)</li>
 *   <li>거래세(농어촌특별세 포함): 매도 체결금액 × 세율, 원 미만 절사. 시행일·시장별 세율 테이블.</li>
 * </ul>
 *
 * 세율 (매도 시, 농특세 포함 합계)
 * <pre>
 *            KOSPI   KOSDAQ
 * 2024       0.18%   0.18%
 * 2025       0.15%   0.15%
 * 2026~      0.20%   0.20%   (KOSPI 거래세 0.05% + 농특세 0.15%)
 * </pre>
 */
public final class FeeTaxPolicy {

    public static final BigDecimal DEFAULT_FEE_RATE = new BigDecimal("0.00015");

    record TaxRate(LocalDate from, MarketType market, BigDecimal rate) {
    }

    private static final List<TaxRate> TAX_TABLE = List.of(
            new TaxRate(LocalDate.of(2026, 1, 1), MarketType.KOSPI, new BigDecimal("0.0020")),
            new TaxRate(LocalDate.of(2026, 1, 1), MarketType.KOSDAQ, new BigDecimal("0.0020")),
            new TaxRate(LocalDate.of(2025, 1, 1), MarketType.KOSPI, new BigDecimal("0.0015")),
            new TaxRate(LocalDate.of(2025, 1, 1), MarketType.KOSDAQ, new BigDecimal("0.0015")),
            new TaxRate(LocalDate.of(2024, 1, 1), MarketType.KOSPI, new BigDecimal("0.0018")),
            new TaxRate(LocalDate.of(2024, 1, 1), MarketType.KOSDAQ, new BigDecimal("0.0018"))
    );

    private final BigDecimal feeRate;

    public FeeTaxPolicy(BigDecimal feeRate) {
        this.feeRate = feeRate == null ? DEFAULT_FEE_RATE : feeRate;
    }

    public BigDecimal feeRate() {
        return feeRate;
    }

    public BigDecimal fee(BigDecimal amount) {
        return amount.multiply(feeRate).setScale(0, RoundingMode.FLOOR);
    }

    public BigDecimal sellTax(BigDecimal amount, MarketType market, LocalDate tradeDate) {
        return amount.multiply(taxRate(market, tradeDate)).setScale(0, RoundingMode.FLOOR);
    }

    public static BigDecimal taxRate(MarketType market, LocalDate date) {
        return TAX_TABLE.stream()
                .filter(t -> t.market() == market && !date.isBefore(t.from()))
                .findFirst() // 테이블은 시행일 내림차순
                .map(TaxRate::rate)
                .orElse(new BigDecimal("0.0018"));
    }
}
