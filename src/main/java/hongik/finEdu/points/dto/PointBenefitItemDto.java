package hongik.finEdu.points.dto;

import hongik.finEdu.points.domain.PointBenefitCode;

public record PointBenefitItemDto(
        PointBenefitCode code,
        int pointsRequired,
        String benefitName,
        String description
) {
}
