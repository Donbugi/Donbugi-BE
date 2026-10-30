package hongik.finEdu.quiz.daily.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyQuizSessionResponse(
        String sessionId,
        LocalDate date,
        List<DailyQuizItemView> items,
        int answeredCount,
        int correctCount,
        boolean completed
) {
}
