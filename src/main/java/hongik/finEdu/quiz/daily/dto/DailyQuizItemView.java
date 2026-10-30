package hongik.finEdu.quiz.daily.dto;

import java.util.List;

/**
 * 오늘의 과제 1문항 (FE 표시용).
 * 정답(correctIndex)·해설은 <b>답안 제출 후에만</b> 채워진다 — 클라이언트 자기채점 어뷰징 방지.
 */
public record DailyQuizItemView(
        int order,
        Long articleId,
        String articleTitle,
        String question,
        List<String> options,
        boolean answered,
        Integer selectedIndex,
        Boolean correct,
        Integer correctIndex,
        String explanation
) {
}
