package hongik.finEdu.points.dto;

/**
 * 교환 요청: benefitCode는 {@link hongik.finEdu.points.domain.PointBenefitCode} 이름과 동일한 문자열
 * (예: CONVENIENCE_DISCOUNT).
 */
public record PointRedeemRequest(String userId, String email, String benefitCode) {

    /** 인증 토큰 기준 userId 로 교체 (body 의 userId 는 생략 가능) */
    public PointRedeemRequest withUserId(String uid) {
        return new PointRedeemRequest(uid, email, benefitCode);
    }
}
