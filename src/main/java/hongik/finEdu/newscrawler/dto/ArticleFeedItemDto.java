package hongik.finEdu.newscrawler.dto;

import java.util.List;

/**
 * 목록·상세 카드용 공개 응답 (크롤링 DB 기준).
 * DB에 해시태그 전용 컬럼은 없어 {@code tags}는 비어 있지 않을 때 카테고리명 1개만 넣음 (추후 확장 가능).
 */
public record ArticleFeedItemDto(
        Long articleId,
        String category,
        String title,
        String source,
        String publishedAt,
        /** 화면 본문: 요약이 있으면 요약, 없으면 본문 일부 */
        String body,
        List<String> tags,
        String originalUrl
) {
}
