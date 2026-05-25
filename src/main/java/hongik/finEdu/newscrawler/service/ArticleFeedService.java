package hongik.finEdu.newscrawler.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.newscrawler.config.CrawlerProperties;
import hongik.finEdu.newscrawler.dto.ArticleFeedItemDto;
import hongik.finEdu.newscrawler.dto.CategoryArticlesDto;
import hongik.finEdu.newscrawler.entity.Article;
import hongik.finEdu.newscrawler.repository.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleFeedService {

    private final ArticleRepository articleRepository;
    private final CrawlerProperties crawlerProperties;

    @Transactional(readOnly = true)
    public List<ArticleFeedItemDto> findLatest(int limit) {
        List<Article> rows = articleRepository.findAllByOrderByCollectedAtDesc(PageRequest.of(0, limit));
        return rows.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ArticleFeedItemDto findById(Long articleId) {
        Article a = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        return toDto(a);
    }

    /**
     * 설정된 크롤러 카테고리 순서대로, 카테고리별 최신 {@code perCategory}건.
     */
    @Transactional(readOnly = true)
    public List<CategoryArticlesDto> findLatestByEachCategory(int perCategory) {
        List<CategoryArticlesDto> out = new ArrayList<>();
        for (CrawlerProperties.CategoryEntry entry : crawlerProperties.getCategories()) {
            String cat = entry.getName();
            List<Article> rows = articleRepository.findAllByCategoryOrderByCollectedAtDesc(
                    cat, PageRequest.of(0, perCategory));
            out.add(new CategoryArticlesDto(cat, rows.stream().map(this::toDto).toList()));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<ArticleFeedItemDto> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return findLatest(limit);
        }
        int n = Math.min(50, Math.max(1, limit));
        return articleRepository.searchByKeyword(query.trim(), PageRequest.of(0, n)).stream()
                .map(this::toDto)
                .toList();
    }

    private ArticleFeedItemDto toDto(Article a) {
        List<String> tags = new ArrayList<>();
        if (a.getCategory() != null && !a.getCategory().isBlank()) {
            tags.add(a.getCategory());
        }
        return new ArticleFeedItemDto(
                a.getArticleId(),
                a.getCategory(),
                a.getTitle(),
                a.getPress(),
                a.getPublishedAt(),
                a.getCollectedAt(),
                blankToNull(a.getSummary()),
                safeContent(a.getContent()),
                tags,
                a.getUrl(),
                estimateSentiment(a)
        );
    }

    /** 키워드 기반 간단 감성 추정 (0–100, 높을수록 긍정) */
    private static Integer estimateSentiment(Article a) {
        String text = ((a.getTitle() != null ? a.getTitle() : "") + " "
                + (a.getSummary() != null ? a.getSummary() : "")).toLowerCase();
        if (text.isBlank()) {
            return null;
        }
        int score = 55;
        if (text.contains("상승") || text.contains("증가") || text.contains("호조") || text.contains("회복")) {
            score += 15;
        }
        if (text.contains("하락") || text.contains("감소") || text.contains("약세") || text.contains("우려")) {
            score -= 15;
        }
        if (text.contains("급등") || text.contains("최고") || text.contains("호재")) {
            score += 10;
        }
        if (text.contains("급락") || text.contains("최저") || text.contains("악재")) {
            score -= 10;
        }
        return Math.max(20, Math.min(90, score));
    }

    private static String safeContent(String content) {
        return content != null ? content : "";
    }

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s;
    }
}
