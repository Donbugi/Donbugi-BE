package hongik.finEdu.quiz.stats.dto;

import java.time.Instant;

/** 오답 노트 1건 (최근 구간 필터 후 응답) */
public record QuizWrongNoteDto(
        String id,
        String question,
        String userAnswer,
        String correctAnswer,
        String explanation,
        Instant answeredAt
) {
}
