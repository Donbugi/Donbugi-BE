package hongik.finEdu;

import hongik.finEdu.newscrawler.service.CrawlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableResilientMethods
@RequiredArgsConstructor
public class FinEduApplication implements ApplicationRunner {
	private final CrawlerService crawlerService;

	public static void main(String[] args) {
		SpringApplication.run(FinEduApplication.class, args);
	}
	@Override
	public void run(ApplicationArguments args) {
		log.info("앱 시작: 크롤링 실행");
		crawlerService.crawlAll();
	}
}
