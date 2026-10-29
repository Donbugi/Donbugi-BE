package hongik.finEdu.quiz.daily.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 오늘의 과제 답안 제출. order 1~3, selectedIndex 0~3 */
public record DailyQuizAnswerRequest(
        @NotNull @Min(1) @Max(3) Integer order,
        @NotNull @Min(0) @Max(9) Integer selectedIndex
) {
}
