package hongik.finEdu.news.interest.dto;

import java.time.YearMonth;
import java.util.List;

/** 최근 한 달 관심 뉴스 동향 + AI 한마디 */
public record NewsInterestTrendsResponse(
        YearMonth month,
        List<NewsInterestTopicDto> topics,
        String aiInsight
) {
}
