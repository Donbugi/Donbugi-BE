package hongik.finEdu.newscrawler.controller;

import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.newscrawler.dto.ArticleFeedItemDto;
import hongik.finEdu.newscrawler.dto.CategoryArticlesDto;
import hongik.finEdu.newscrawler.service.ArticleFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = OpenApiTags.NEWS)
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleFeedController {

    private final ArticleFeedService articleFeedService;

    @Operation(
            summary = "최신 뉴스 목록",
            description = "수집 시각(collectedAt) 내림차순. limit 기본 30, 최대 100. 인증 불필요.")
    @GetMapping("/latest")
    public ResponseEntity<List<ArticleFeedItemDto>> latest(
            @Parameter(description = "건수", example = "30")
            @RequestParam(defaultValue = "30") int limit) {
        int n = Math.min(100, Math.max(1, limit));
        return ResponseEntity.ok(articleFeedService.findLatest(n));
    }

    @Operation(
            summary = "카테고리별 최신 뉴스",
            description = "crawler.categories 순(금융·증권·산업·부동산·글로벌경제·생활경제)으로 카테고리마다 N건.")
    @GetMapping("/by-category")
    public ResponseEntity<List<CategoryArticlesDto>> byCategory(
            @Parameter(description = "카테고리당 건수", example = "10")
            @RequestParam(defaultValue = "10") int perCategory) {
        int n = Math.min(50, Math.max(1, perCategory));
        return ResponseEntity.ok(articleFeedService.findLatestByEachCategory(n));
    }

    @Operation(
            summary = "뉴스 검색",
            description = "제목·요약·카테고리 키워드 LIKE 검색. q 필수. sentimentPositivePercent 포함(키워드 추정).")
    @GetMapping("/search")
    public ResponseEntity<List<ArticleFeedItemDto>> search(
            @Parameter(description = "검색어", example = "반도체", required = true)
            @RequestParam String q,
            @Parameter(description = "최대 건수", example = "30")
            @RequestParam(defaultValue = "30") int limit) {
        int n = Math.min(50, Math.max(1, limit));
        return ResponseEntity.ok(articleFeedService.search(q, n));
    }

    @Operation(
            summary = "기사 상세",
            description = "articleId로 단건 조회. content(본문), summary, tags, sentimentPositivePercent 포함.")
    @GetMapping("/{articleId}")
    public ResponseEntity<ArticleFeedItemDto> one(
            @Parameter(description = "기사 ID", example = "42")
            @PathVariable Long articleId) {
        return ResponseEntity.ok(articleFeedService.findById(articleId));
    }
}
