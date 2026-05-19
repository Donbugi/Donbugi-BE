package hongik.finEdu.quiz.dto;

import java.util.List;

/**
 * 한 번에 보여 줄 객관식 1문항 (프론트: Q1 · 보기 버튼 나열).
 */
public record QuizSessionItemDto(
        int order,
        Long articleId,
        String articleTitle,
        String question,
        List<String> options,
        int correctIndex,
        String explanation
) {
}
