package hongik.finEdu.quiz.daily.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 기사 상세 퀴즈 답안 제출. questionIndex 는 기사 퀴즈 배열의 인덱스(기본 0) */
public record QuizAnswerRequest(
        @Min(0) @Max(9) Integer questionIndex,
        @NotNull @Min(0) @Max(9) Integer selectedIndex
) {
}
