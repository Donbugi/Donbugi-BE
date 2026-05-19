package hongik.finEdu.newscrawler.controller;

import hongik.finEdu.newscrawler.dto.ArticleFeedItemDto;
import hongik.finEdu.newscrawler.dto.CategoryArticlesDto;
import hongik.finEdu.newscrawler.service.ArticleFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleFeedController {

    private final ArticleFeedService articleFeedService;

    /**
     * 전체 최신 뉴스 (수집 시각 기준 내림차순)
     * GET /api/articles/latest?limit=30
     */
    @GetMapping("/latest")
    public ResponseEntity<List<ArticleFeedItemDto>> latest(
            @RequestParam(defaultValue = "30") int limit) {
        int n = Math.min(100, Math.max(1, limit));
        return ResponseEntity.ok(articleFeedService.findLatest(n));
    }

    /**
     * crawler.categories 순서대로, 카테고리마다 최신 N건
     * GET /api/articles/by-category?perCategory=10
     */
    @GetMapping("/by-category")
    public ResponseEntity<List<CategoryArticlesDto>> byCategory(
            @RequestParam(defaultValue = "10") int perCategory) {
        int n = Math.min(50, Math.max(1, perCategory));
        return ResponseEntity.ok(articleFeedService.findLatestByEachCategory(n));
    }

    /**
     * 기사 단건 (상세 화면). 목록과 동일 스키마로 전체 본문·요약 포함.
     * GET /api/articles/{articleId}
     */
    @GetMapping("/{articleId}")
    public ResponseEntity<ArticleFeedItemDto> one(@PathVariable Long articleId) {
        return ResponseEntity.ok(articleFeedService.findById(articleId));
    }
}
