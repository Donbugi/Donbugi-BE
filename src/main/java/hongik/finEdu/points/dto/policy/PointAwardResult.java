package hongik.finEdu.points.dto.policy;

/** 멱등 지급 시도 결과 */
public record PointAwardResult(boolean awarded, int amount, int balanceAfter) {
}
