package hongik.finEdu.trading.domain;

public enum OrderStatus {
    /** 미체결 (체결 대기) */
    PENDING,
    /** 일부 체결, 잔량 대기 */
    PARTIALLY_FILLED,
    /** 전량 체결 */
    FILLED,
    /** 취소 (일부 체결 후 잔량 취소 포함 — filledQuantity 로 구분) */
    CANCELLED,
    /** 접수 거부 (호가 잔량 없음 등) */
    REJECTED;

    public boolean isOpen() {
        return this == PENDING || this == PARTIALLY_FILLED;
    }
}
