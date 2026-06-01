package hongik.finEdu.newscrawler.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "뉴스 기사 (목록·상세·검색 공통)")
public record ArticleFeedItemDto(
        @Schema(description = "기사 ID", example = "42")
        Long articleId,
        @Schema(description = "카테고리", example = "금융")
        String category,
        @Schema(description = "제목")
        String title,
        @Schema(description = "언론사", example = "한국경제")
        String source,
        @Schema(description = "기사 게시 시각 (원문)", example = "2026-05-20 09:30")
        String publishedAt,
        @Schema(description = "크롤러 수집 시각")
        LocalDateTime collectedAt,
        @Schema(description = "요약 (KoBART 등, 없으면 null)")
        String summary,
        @Schema(description = "본문 전체")
        String content,
        @Schema(description = "태그 (현재는 category 1개)")
        List<String> tags,
        @Schema(description = "원문 기사 URL", example = "https://n.news.naver.com/mnews/article/...")
        String url,
        @Schema(description = "긍정 감성 추정 0–100, null 가능")
        Integer sentimentPositivePercent
) {
}
