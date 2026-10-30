package hongik.finEdu.trading.dto;

import java.math.BigDecimal;

/** 정정 주문. 둘 중 하나 이상 지정. 생략한 값은 원주문(잔량/가격) 유지 */
public record OrderModifyRequest(BigDecimal limitPrice, Integer quantity) {
}
