package hongik.finEdu.trading.market;

/**
 * KRX 정규시장 세션 (시간외 매매·NXT 대체거래소는 모의투자 범위 밖).
 */
public enum MarketSession {
    /** 08:30~09:00 장 시작 동시호가 — 주문 접수만, 09:00 시가 단일가로 일괄 체결 */
    PRE_OPEN_AUCTION,
    /** 09:00~15:20 접속매매 — 호가 기반 즉시/조건 체결 */
    REGULAR,
    /** 15:20~15:30 장 마감 동시호가 — 주문 접수만, 15:30 종가 단일가로 일괄 체결 */
    CLOSING_AUCTION,
    /** 그 외 시간·휴장일 — 주문 불가 */
    CLOSED;

    public boolean acceptsOrders() {
        return this != CLOSED;
    }

    public boolean isAuction() {
        return this == PRE_OPEN_AUCTION || this == CLOSING_AUCTION;
    }
}
