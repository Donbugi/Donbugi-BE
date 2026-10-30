package hongik.finEdu.points.dto;

import hongik.finEdu.points.domain.PointBenefitCode;

/**
 * 포인트 차감·교환 row 저장까지 완료된 트랜잭션 결과 (메일은 outbox 로 비동기 발송).
 * replayed=true 면 같은 Idempotency-Key 재요청으로 기존 교환 결과를 돌려준 것.
 */
public record RedeemLedgerResult(
        String redemptionRef,
        String userId,
        String email,
        PointBenefitCode benefitCode,
        int pointsSpent,
        int balanceAfter,
        boolean replayed
) {
}
