package hongik.finEdu.trading.engine;

import hongik.finEdu.trading.entity.TradingOrder;

import java.math.BigDecimal;

/**
 * 주문 처리 결과.
 *
 * @param order       처리 후 주문 상태
 * @param filledNow   이번 호출에서 체결된 수량
 * @param amountNow   이번 호출 체결금액 (수수료·세금 제외)
 * @param feeNow      이번 호출 수수료
 * @param taxNow      이번 호출 세금
 * @param replayed    멱등키 재요청으로 기존 주문을 그대로 돌려준 경우
 */
public record ExecutionReport(
        TradingOrder order,
        int filledNow,
        BigDecimal amountNow,
        BigDecimal feeNow,
        BigDecimal taxNow,
        boolean replayed
) {
    public static ExecutionReport replay(TradingOrder order) {
        return new ExecutionReport(order, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true);
    }
}
