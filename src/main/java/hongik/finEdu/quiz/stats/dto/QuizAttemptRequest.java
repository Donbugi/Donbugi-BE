package hongik.finEdu.quiz.stats.dto;

/**
 * 퀴즈 1회 채점 결과 제출 (Redis에만 반영).
 * 오답이면 question / userAnswer / correctAnswer 는 필수.
 */
public record QuizAttemptRequest(
        String userId,
        boolean correct,
        String question,
        String userAnswer,
        String correctAnswer,
        String explanation
) {
}
