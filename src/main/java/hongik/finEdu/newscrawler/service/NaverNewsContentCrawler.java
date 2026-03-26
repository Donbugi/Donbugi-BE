package hongik.finEdu.newscrawler.service;

import hongik.finEdu.newscrawler.entity.ArticleDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@Component
public class NaverNewsContentCrawler {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36";

    private static final int MAX_RETRY = 3;

    @Value("${crawler.delay-min:1500}")
    private long delayMin;

    @Value("${crawler.delay-max:3000}")
    private long delayMax;

    /**
     * URL 목록: Virtual Thread 병렬 수집
     * Virtual Thread: I/O 대기 중 carrier thread를 반납하므로
     * 플랫폼 스레드 수와 무관하게 수천 개의 동시 요청 처리 가능 (JEP 444, Java 21)
     */
    public List<ArticleDto> crawlAll(List<String> urls, String category) {
        List<ArticleDto> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ArticleDto>> futures = urls.stream()
                    .map(url -> executor.submit(() -> crawlWithRetry(url, category)))
                    .toList();

            for (Future<ArticleDto> future : futures) {
                try {
                    ArticleDto dto = future.get();
                    if (dto != null && !isBlank(dto.getTitle())) {
                        results.add(dto);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    log.warn("수집 오류: {}", e.getMessage());
                }
            }
        }

        log.info("[{}] 본문 수집 완료: {}개", category, results.size());
        return results;
    }

    /**
     * 단순 재시도 3회 - 네트워크 순단, 일시적 503 등 대응
     */
    private ArticleDto crawlWithRetry(String url, String category) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                return crawlOne(url, category);
            } catch (IOException e) {
                log.warn("[{}] 실패 ({}/{}): {} - {}", category, attempt, MAX_RETRY, url, e.getMessage());
                if (attempt == MAX_RETRY) return null;
                try {
                    Thread.sleep(delayMin); // 재시도 전 대기
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 기사 1건 수집
     */
    private ArticleDto crawlOne(String url, String category) throws IOException, InterruptedException {
        // 랜덤 딜레이: 요청 패턴을 불규칙하게 만들어 차단 방지
        long delay = delayMin + (long) (Math.random() * (delayMax - delayMin));
        Thread.sleep(delay); // Virtual Thread → carrier thread 반납
        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(10_000)
                .get();

        // 제목
        String title = select(doc, "h2#title_area span");
        // 본문
        String content = "";
        Element contentEl = doc.selectFirst("article#dic_area");
        if (contentEl != null) {
            contentEl.select("span.end_photo_org").remove();
            contentEl.select("div.ad").remove();
            content = contentEl.text()
                    .replace("\uFEFF", "")
                    .strip();
        }

        // 신문사
        String press = "";
        Element pressEl = doc.selectFirst("div.media_end_head_top a img");
        if (pressEl != null) press = pressEl.attr("alt").strip();

        // 기자명
        String journalist = select(doc, "span.byline_s");

        // 날짜
        String publishedAt = "";
        Element dateEl = doc.selectFirst("span.media_end_head_info_datestamp_time");
        if (dateEl != null) publishedAt = dateEl.attr("data-date-time");

        log.debug("[{}] 수집: {}", category, title);

        return ArticleDto.builder()
                .url(url)
                .title(title)
                .content(content)
                .press(press)
                .journalist(journalist)
                .publishedAt(publishedAt)
                .category(category)
                .build();
    }

    private String select(Document doc, String cssQuery) {
        Element el = doc.selectFirst(cssQuery);
        return el != null ? el.text().strip() : "";
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
