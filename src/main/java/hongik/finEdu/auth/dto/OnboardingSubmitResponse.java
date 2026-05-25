package hongik.finEdu.auth.dto;

public record OnboardingSubmitResponse(
        int characterLevel,
        String characterEmoji,
        String characterName,
        String characterTag,
        int totalScore
) {
}
