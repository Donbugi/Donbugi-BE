package hongik.finEdu.points.dto;

import hongik.finEdu.points.domain.PointBenefitCode;

public record PointRedeemResponseDto(
        String redemptionRef,
        String userId,
        String email,
        PointBenefitCode benefitCode,
        String benefitName,
        int pointsSpent,
        int balanceAfter,
        boolean mailSent,
        String mailNotice
) {
}
