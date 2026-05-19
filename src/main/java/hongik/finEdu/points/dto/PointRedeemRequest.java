package hongik.finEdu.points.dto;

/**
 * 교환 요청: benefitCode는 {@link hongik.finEdu.points.domain.PointBenefitCode} 이름과 동일한 문자열
 * (예: CONVENIENCE_DISCOUNT).
 */
public record PointRedeemRequest(String userId, String email, String benefitCode) {
}
