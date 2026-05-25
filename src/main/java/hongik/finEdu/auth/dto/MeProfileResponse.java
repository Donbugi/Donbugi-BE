package hongik.finEdu.auth.dto;

import java.time.LocalDateTime;

public record MeProfileResponse(
        String userId,
        String email,
        String nickname,
        boolean onboardingCompleted,
        Integer characterLevel,
        String characterEmoji,
        String characterName,
        String characterTag,
        int finIqBalance,
        int finIqLevel,
        int pointsToNextLevel,
        int finIqProgressPercent,
        LocalDateTime onboardingCompletedAt
) {
}
