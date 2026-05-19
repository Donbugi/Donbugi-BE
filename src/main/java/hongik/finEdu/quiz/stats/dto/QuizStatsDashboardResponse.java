package hongik.finEdu.quiz.stats.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 과제 대시보드: 동일 요일 기준 7일 구간(저번주 같은 요일 0시 ~ 오늘 23:59:59) + 이번 달 처리.
 */
public record QuizStatsDashboardResponse(
        LocalDate rollingWindowStart,
        LocalDate rollingWindowEnd,
        QuizRateSummaryDto rollingWeek,
        QuizRateSummaryDto thisMonth,
        List<QuizWrongNoteDto> wrongNotesInWindow
) {
}
