package hongik.finEdu.quiz.stats.model;

/**
 * Redis ZSET 멤버(JSON). score는 answeredAtEpochMilli와 동일하게 둔다.
 */
public record QuizWrongNoteStored(
        String id,
        String question,
        String userAnswer,
        String correctAnswer,
        String explanation,
        long answeredAtEpochMilli
) {
}
