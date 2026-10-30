package hongik.finEdu.trading.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * KRX 유가증권·코스닥 호가가격단위 (2023-01-25 개편 이후, 시장 공통).
 *
 * <pre>
 *          ~   2,000 원 미만 :     1 원
 *   2,000  ~   5,000 원 미만 :     5 원
 *   5,000  ~  20,000 원 미만 :    10 원
 *  20,000  ~  50,000 원 미만 :    50 원
 *  50,000  ~ 200,000 원 미만 :   100 원
 * 200,000  ~ 500,000 원 미만 :   500 원
 * 500,000 원 이상            : 1,000 원
 * </pre>
 */
public final class TickSizePolicy {

    private static final long[][] TABLE = {
            {2_000, 1},
            {5_000, 5},
            {20_000, 10},
            {50_000, 50},
            {200_000, 100},
            {500_000, 500},
            {Long.MAX_VALUE, 1_000},
    };

    private TickSizePolicy() {
    }

    public static BigDecimal tickSize(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다: " + price);
        }
        for (long[] row : TABLE) {
            if (price.compareTo(BigDecimal.valueOf(row[0])) < 0) {
                return BigDecimal.valueOf(row[1]);
            }
        }
        return BigDecimal.valueOf(1_000);
    }

    /** 호가단위에 맞는 가격인지 (정수 원 + 단위 배수) */
    public static boolean isValidTick(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            return false;
        }
        if (price.stripTrailingZeros().scale() > 0) {
            return false;
        }
        return price.remainder(tickSize(price)).signum() == 0;
    }

    /** 단위 아래로 절사 (예: 상한가 계산) */
    public static BigDecimal floorToTick(BigDecimal price) {
        BigDecimal tick = tickSize(price);
        BigDecimal floored = price.divide(tick, 0, RoundingMode.FLOOR).multiply(tick);
        // 절사 결과가 하위 구간으로 내려가면 그 구간의 단위가 적용되므로 그대로 유효
        return floored.setScale(0, RoundingMode.UNNECESSARY);
    }

    /** 단위 위로 절상 (예: 하한가 계산) */
    public static BigDecimal ceilToTick(BigDecimal price) {
        BigDecimal tick = tickSize(price);
        BigDecimal ceiled = price.divide(tick, 0, RoundingMode.CEILING).multiply(tick);
        return ceiled.setScale(0, RoundingMode.UNNECESSARY);
    }

    /** 한 틱 위 가격 */
    public static BigDecimal up(BigDecimal price, int ticks) {
        BigDecimal p = price;
        for (int i = 0; i < ticks; i++) {
            p = p.add(tickSize(p));
        }
        return p;
    }

    /** n 틱 아래 가격. 구간 경계(예: 2,000원)의 한 틱 아래는 하위 구간 단위가 적용된 1,999원 */
    public static BigDecimal down(BigDecimal price, int ticks) {
        BigDecimal p = price;
        for (int i = 0; i < ticks; i++) {
            BigDecimal below = p.subtract(BigDecimal.ONE);
            if (below.signum() <= 0) {
                break;
            }
            p = p.subtract(tickSize(below));
        }
        return p;
    }
}
