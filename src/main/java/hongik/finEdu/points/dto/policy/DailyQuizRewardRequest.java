package hongik.finEdu.points.dto.policy;

import java.util.List;

public record DailyQuizRewardRequest(String userId, String sessionId, List<DailyQuizSlotResult> results) {
}
