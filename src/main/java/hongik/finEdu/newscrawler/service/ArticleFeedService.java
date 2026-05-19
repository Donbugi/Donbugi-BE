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
                null
        );
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
