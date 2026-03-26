package hongik.finEdu.newscrawler.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerService crawlerService;
    // 오전 6시 ~ 오후 9시, 3시간마다
    @Scheduled(cron = "0 0 6,9,12,15,18,21 * * *")
    public void run() {
        log.info("[스케줄러] 크롤링 트리거");
        crawlerService.crawlAll();
    }
}
