package hongik.finEdu.trading.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 체결 결과 (여러 호가에 걸쳐 체결될 수 있음).
 */
public record Fill(List<Slice> slices) {

    public record Slice(BigDecimal price, long quantity) {
        public BigDecimal amount() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public static Fill none() {
        return new Fill(List.of());
    }

    public long quantity() {
        return slices.stream().mapToLong(Slice::quantity).sum();
    }

    /** 체결금액 합계 (수수료·세금 제외) */
    public BigDecimal amount() {
        return slices.stream().map(Slice::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 가중평균 체결가 (표시용, 소수 2자리) */
    public BigDecimal averagePrice() {
        long q = quantity();
        return q == 0 ? BigDecimal.ZERO : amount().divide(BigDecimal.valueOf(q), 2, RoundingMode.HALF_UP);
    }

    public boolean isEmpty() {
        return quantity() == 0;
    }
}
