package hongik.finEdu.points.dto;

import hongik.finEdu.points.domain.PointBenefitCode;

/**
 * 포인트 차감·교환 row 저장까지 완료된 트랜잭션 결과 (메일 발송 전).
 */
public record RedeemLedgerResult(
        String redemptionRef,
        String userId,
        String email,
        PointBenefitCode benefitCode,
        int pointsSpent,
        int balanceAfter
) {
}
