package hongik.finEdu.newscrawler.service;

import hongik.finEdu.newscrawler.config.CrawlerProperties;
import hongik.finEdu.newscrawler.entity.ArticleDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlerService {

    private final NaverNewsListCrawler listCrawler;
    private final NaverNewsContentCrawler contentCrawler;
    private final ArticleService articleService;
    private final CrawlerProperties crawlerProperties;

    private Map<String, String> getCategories() {
        return crawlerProperties.getCategoriesAsMap();
    }

    public void crawlAll() {
        log.info("========== 크롤링 시작 ==========");
        long start    = System.currentTimeMillis();
        int total     = 0;

        for (Map.Entry<String, String> entry : getCategories().entrySet()) {
            String name = entry.getKey();
            String url  = entry.getValue();
            try {
                // 1단계: 목록 URL 수집
                List<String> urls = listCrawler.getArticleUrls(url, name);
                if (urls.isEmpty()) continue;
                // 2단계: 본문 병렬 수집 (Virtual Thread)
                List<ArticleDto> articles = contentCrawler.crawlAll(urls, name);
                // 3단계: DB 저장
                int saved = articleService.saveAll(articles);
                total += saved;
                log.info("[{}] 저장 {}개 / 수집 {}개", name, saved, articles.size());
            } catch (Exception e) {
                log.error("[{}] 실패: {}", name, e.getMessage(), e);
            }
        }
        log.info("========== 완료: 총 {}개 / {}ms ==========",
                total, System.currentTimeMillis() - start);
    }
}
