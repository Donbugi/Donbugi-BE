package hongik.finEdu.newscrawler.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 목록·상세 공개 응답. 메인 피드에도 요약·전체 본문을 함께 내려 클라이언트가 카드 전체 노출에 쓸 수 있음.
 * DB에 해시태그·감성 전용 컬럼이 없어 {@code tags}는 카테고리명 1개만 넣고, {@code sentimentPositivePercent}는 미계산 시 null.
 */
public record ArticleFeedItemDto(
        Long articleId,
        String category,
        String title,
        String source,
        String publishedAt,
        LocalDateTime collectedAt,
        /** AI/크롤러 요약 (없으면 null 또는 빈 문자열) */
        String summary,
        /** 원문 HTML/텍스트 전체 (절단 없음) */
        String content,
        List<String> tags,
        String originalUrl,
        /** 긍정 비율 0–100, 미보유 시 null */
        Integer sentimentPositivePercent
) {
}
