package hongik.finEdu.points.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 표에 정의된 6종 혜택. API에는 enum 이름 문자열로 전달 (예: CONVENIENCE_DISCOUNT).
 */
@Getter
@RequiredArgsConstructor
public enum PointBenefitCode {

    CONVENIENCE_DISCOUNT(600, "편의점 할인", "GS25·CU 1,000원 할인"),
    COFFEE_COUPON(800, "커피 쿠폰", "스타벅스·메가커피 아메리카노 1잔"),
    EASY_PAY_CASHBACK(1_000, "간편결제 캐시백", "카카오페이·토스 500원 캐시백"),
    DATA_COUPON(1_500, "데이터 쿠폰", "통신사 1GB 충전"),
    SHOPPING_DISCOUNT(2_000, "쇼핑 할인", "네이버쇼핑 5,000원"),
    OTT_SUBSCRIPTION(3_000, "OTT 이용권", "웨이브·티빙 1개월");

    private final int pointsRequired;
    private final String benefitName;
    private final String description;

    @JsonValue
    public String toJson() {
        return name();
    }
}
