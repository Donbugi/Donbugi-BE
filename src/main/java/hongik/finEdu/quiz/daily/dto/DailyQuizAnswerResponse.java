package hongik.finEdu.quiz.daily.dto;

public record DailyQuizAnswerResponse(
        int order,
        boolean correct,
        int correctIndex,
        String explanation,
        int pointsAwarded,
        int bonusAwarded,
        int balanceAfter,
        boolean sessionCompleted
) {
}
