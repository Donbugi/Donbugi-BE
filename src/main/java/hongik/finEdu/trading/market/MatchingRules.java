package hongik.finEdu.trading.market;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 순수 체결 규칙 (Spring·DB 의존 없음 → 단위 테스트 대상).
 *
 * <ul>
 *   <li>시장가 매수: 매도호가를 낮은 가격부터 잔량만큼 소진 (호가 잔량을 넘는 수량은 미체결 → IOC 취소)</li>
 *   <li>시장가 매도: 매수호가를 높은 가격부터 소진</li>
 *   <li>지정가 매수: 지정가 이하 매도호가만 소진 (가격 개선 체결).
 *       호가에 없더라도 직전 확인 이후 저가가 지정가 이하였다면 그 사이 지정가에 거래가 있었던 것으로 보고 지정가로 체결</li>
 *   <li>지정가 매도: 대칭</li>
 *   <li>동시호가: 단일가(시가/종가)로 전량 체결 — 지정가 조건 충족 시</li>
 * </ul>
 */
public final class MatchingRules {

    private MatchingRules() {
    }

    public static Fill marketBuy(OrderBook book, long quantity) {
        return walk(book.asks(), quantity, null, true);
    }

    public static Fill marketSell(OrderBook book, long quantity) {
        return walk(book.bids(), quantity, null, false);
    }

    /**
     * @param intervalLow 직전 체결 확인 이후 관측된 최저가 (없으면 null)
     */
    public static Fill limitBuy(OrderBook book, BigDecimal limit, long quantity, BigDecimal intervalLow) {
        Fill fromBook = walk(book.asks(), quantity, limit, true);
        long remaining = quantity - fromBook.quantity();
        if (remaining > 0 && intervalLow != null && intervalLow.compareTo(limit) <= 0) {
            return merge(fromBook, new Fill.Slice(limit, remaining));
        }
        return fromBook;
    }

    /**
     * @param intervalHigh 직전 체결 확인 이후 관측된 최고가 (없으면 null)
     */
    public static Fill limitSell(OrderBook book, BigDecimal limit, long quantity, BigDecimal intervalHigh) {
        Fill fromBook = walk(book.bids(), quantity, limit, false);
        long remaining = quantity - fromBook.quantity();
        if (remaining > 0 && intervalHigh != null && intervalHigh.compareTo(limit) >= 0) {
            return merge(fromBook, new Fill.Slice(limit, remaining));
        }
        return fromBook;
    }

    /**
     * 단일가 매매(동시호가). 시장가(limit == null)는 무조건, 지정가는 매수 limit ≥ 단일가 / 매도 limit ≤ 단일가일 때 전량 체결.
     */
    public static Fill auction(boolean buy, BigDecimal limit, long quantity, BigDecimal auctionPrice) {
        if (auctionPrice == null || auctionPrice.signum() <= 0) {
            return Fill.none();
        }
        boolean eligible = limit == null
                || (buy ? limit.compareTo(auctionPrice) >= 0 : limit.compareTo(auctionPrice) <= 0);
        return eligible ? new Fill(List.of(new Fill.Slice(auctionPrice, quantity))) : Fill.none();
    }

    /** 체결 시 필요한 최대 금액 추정 (시장가 매수 사전 검증용): 소진될 호가들의 금액 합 */
    public static BigDecimal worstCaseBuyAmount(OrderBook book, long quantity) {
        return marketBuy(book, quantity).amount();
    }

    private static Fill walk(List<OrderBook.Level> levels, long quantity, BigDecimal limit, boolean buy) {
        List<Fill.Slice> slices = new ArrayList<>();
        long remaining = quantity;
        for (OrderBook.Level level : levels) {
            if (remaining <= 0) {
                break;
            }
            if (limit != null) {
                boolean crosses = buy ? level.price().compareTo(limit) <= 0 : level.price().compareTo(limit) >= 0;
                if (!crosses) {
                    break; // 정렬되어 있으므로 이후 레벨도 조건 불충족
                }
            }
            long take = Math.min(remaining, level.quantity());
            slices.add(new Fill.Slice(level.price(), take));
            remaining -= take;
        }
        return new Fill(slices);
    }

    private static Fill merge(Fill a, Fill.Slice extra) {
        List<Fill.Slice> all = new ArrayList<>(a.slices());
        all.add(extra);
        return new Fill(all);
    }
}
