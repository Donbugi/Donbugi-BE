package hongik.finEdu.newscrawler.service;

import hongik.finEdu.common.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlerScheduler {

    private final CrawlerService crawlerService;
    private final DistributedLock distributedLock;

    // 오전 6시 ~ 오후 9시(서울), 3시간마다. zone 미지정 시 서버(UTC) 기준으로 돌던 문제 수정
    @Scheduled(cron = "0 0 6,9,12,15,18,21 * * *", zone = "Asia/Seoul")
    public void run() {
        distributedLock.runExclusively("sched:news-crawl", Duration.ofMinutes(30), () -> {
            log.info("[스케줄러] 크롤링 트리거");
            crawlerService.crawlAll();
        });
    }
}
