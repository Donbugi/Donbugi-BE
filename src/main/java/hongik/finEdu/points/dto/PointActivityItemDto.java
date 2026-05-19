package hongik.finEdu.points.dto;

import java.time.LocalDateTime;

/**
 * 적립(EARN) / 사용(SPEND) 통합 최근 내역 (Redis·API 공통 형식).
 */
public record PointActivityItemDto(
        String type,
        int delta,
        String title,
        String detail,
        String relatedRef,
        LocalDateTime occurredAt
) {
}
