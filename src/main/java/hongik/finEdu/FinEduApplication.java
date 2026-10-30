package hongik.finEdu;

import hongik.finEdu.newscrawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@SpringBootApplication
@EnableAsync
@EnableResilientMethods
@RequiredArgsConstructor
public class FinEduApplication implements ApplicationRunner {
	private final CrawlerService crawlerService;

	/** 기동 직후 1회 크롤링 (테스트에서는 false) */
	@Value("${app.crawl-on-startup:true}")
	private boolean crawlOnStartup;

	public static void main(String[] args) {
		SpringApplication.run(FinEduApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!crawlOnStartup) {
			return;
		}
		// 기동(헬스체크 통과)을 막지 않도록 별도 가상 스레드에서 실행
		Thread.ofVirtual().name("startup-crawl").start(() -> {
			log.info("앱 시작: 크롤링 실행");
			try {
				crawlerService.crawlAll();
			} catch (Exception e) {
				log.warn("기동 크롤링 실패: {}", e.getMessage());
			}
		});
	}
}
