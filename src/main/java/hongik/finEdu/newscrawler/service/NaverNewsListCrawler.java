package hongik.finEdu.newscrawler.service;


import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class NaverNewsListCrawler {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Safari/537.36";
    @Value("${crawler.delay-min:1500}")
    private long delayMin;
    public List<String> getArticleUrls(String categoryUrl, String categoryName) {
        List<String> urls = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(categoryUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10_000)
                    .get();

            // 이미지1: a.sa_text_title 에서 href 추출
            Elements anchors = doc.select("a.sa_text_title");

            for (var anchor : anchors) {
                String href = anchor.attr("href");
                // 네이버 뉴스 본문 URL만 필터링
                if (href != null && href.contains("n.news.naver.com")) {
                    urls.add(href);
                }
            }
            log.info("[{}] URL {}개 수집", categoryName, urls.size());
        } catch (IOException e) {
            log.error("[{}] 목록 수집 실패: {}", categoryName, e.getMessage());
        }
        return urls;
    }
}
