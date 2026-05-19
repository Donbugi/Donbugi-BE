package hongik.finEdu.newscrawler.dto;

import java.util.List;

public record CategoryArticlesDto(
        String category,
        List<ArticleFeedItemDto> articles
) {
}
