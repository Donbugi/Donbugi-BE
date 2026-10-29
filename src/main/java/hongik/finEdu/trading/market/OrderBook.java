package hongik.finEdu.trading.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 호가창 스냅샷.
 *
 * @param asks      매도호가 (가격 오름차순: asks[0] = 최우선 매도호가)
 * @param bids      매수호가 (가격 내림차순: bids[0] = 최우선 매수호가)
 * @param synthetic 실제 호가 조회 실패 시 현재가 기준으로 만든 근사 호가인지
 */
public record OrderBook(
        String stockCode,
        List<Level> asks,
        List<Level> bids,
        Instant fetchedAt,
        boolean synthetic
) {

    public record Level(BigDecimal price, long quantity) {
    }

    public OrderBook {
        asks = asks.stream().filter(l -> l.quantity() > 0 && l.price().signum() > 0)
                .sorted(Comparator.comparing(Level::price)).toList();
        bids = bids.stream().filter(l -> l.quantity() > 0 && l.price().signum() > 0)
                .sorted(Comparator.comparing(Level::price).reversed()).toList();
    }

    public BigDecimal bestAsk() {
        return asks.isEmpty() ? null : asks.get(0).price();
    }

    public BigDecimal bestBid() {
        return bids.isEmpty() ? null : bids.get(0).price();
    }

    /**
     * 한 번의 체결 패스에서 여러 주문이 같은 호가 잔량을 중복 소진하지 않도록, 체결분을 차감한 호가를 반환.
     * (호가에 없는 가격으로 체결된 구간 저가/고가 체결분은 차감 대상이 없으므로 무시)
     */
    public OrderBook consume(Fill fill, boolean buy) {
        List<Level> side = new ArrayList<>(buy ? asks : bids);
        for (Fill.Slice slice : fill.slices()) {
            for (int i = 0; i < side.size(); i++) {
                Level l = side.get(i);
                if (l.price().compareTo(slice.price()) == 0) {
                    side.set(i, new Level(l.price(), Math.max(0, l.quantity() - slice.quantity())));
                    break;
                }
            }
        }
        return buy ? new OrderBook(stockCode, side, bids, fetchedAt, synthetic)
                : new OrderBook(stockCode, asks, side, fetchedAt, synthetic);
    }

    /**
     * 실시간 호가를 못 받았을 때의 근사 호가: 현재가를 최우선 매도호가로, 한 틱 아래를 최우선 매수호가로 두고
     * 위·아래로 10단계, 단계별 잔량은 levelQuantity.
     */
    public static OrderBook synthetic(String stockCode, BigDecimal lastPrice, long levelQuantity, Instant at) {
        List<Level> asks = new ArrayList<>();
        List<Level> bids = new ArrayList<>();
        BigDecimal a = lastPrice;
        BigDecimal b = TickSizePolicy.down(lastPrice, 1);
        for (int i = 0; i < 10; i++) {
            asks.add(new Level(a, levelQuantity));
            bids.add(new Level(b, levelQuantity));
            a = TickSizePolicy.up(a, 1);
            b = TickSizePolicy.down(b, 1);
        }
        return new OrderBook(stockCode, asks, bids, at, true);
    }
}
