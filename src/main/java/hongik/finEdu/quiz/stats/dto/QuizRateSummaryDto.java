package hongik.finEdu.quiz.stats.dto;

/** 주간·월간 정답률 요약 */
public record QuizRateSummaryDto(
        int correct,
        int wrong,
        int total,
        int ratePercent
) {
}
