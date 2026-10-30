package hongik.finEdu.trading.market;

import java.math.BigDecimal;

/**
 * 가격제한폭: 기준가(전일 종가) 대비 ±30%.
 * 상한가는 호가단위로 절사, 하한가는 호가단위로 절상한다.
 */
public final class PriceLimitPolicy {

    private static final BigDecimal UP = new BigDecimal("1.30");
    private static final BigDecimal DOWN = new BigDecimal("0.70");

    private PriceLimitPolicy() {
    }

    public record Band(BigDecimal lower, BigDecimal upper) {
        public boolean contains(BigDecimal price) {
            return price.compareTo(lower) >= 0 && price.compareTo(upper) <= 0;
        }
    }

    public static Band band(BigDecimal basePrice) {
        if (basePrice == null || basePrice.signum() <= 0) {
            throw new IllegalArgumentException("기준가가 없습니다");
        }
        BigDecimal upper = TickSizePolicy.floorToTick(basePrice.multiply(UP));
        BigDecimal lower = TickSizePolicy.ceilToTick(basePrice.multiply(DOWN));
        return new Band(lower, upper);
    }
}
