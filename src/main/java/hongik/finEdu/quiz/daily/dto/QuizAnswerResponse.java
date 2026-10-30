package hongik.finEdu.quiz.daily.dto;

public record QuizAnswerResponse(
        Long articleId,
        int questionIndex,
        boolean correct,
        int correctIndex,
        String explanation,
        boolean pointsAwarded,
        int pointsAmount,
        int balanceAfter
) {
}
