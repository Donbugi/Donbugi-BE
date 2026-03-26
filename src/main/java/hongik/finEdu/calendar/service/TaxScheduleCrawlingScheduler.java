package hongik.finEdu.calendar.service;

import hongik.finEdu.calendar.repository.TaxScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 세무일정 크롤링 스케줄러
 *
 * [스케줄 전략]
 * - 매월 1일 새벽 2시: 3개월치 정기 크롤링
 * - 매주 월요일 새벽 3시: 변경사항 반영용 보완 크롤링
 * - 매일 새벽 4시: 오래된 데이터 정리
 *
 * [설계 이유]
 * 세무일정은 월 단위로 고정되어 있지만,
 * 간혹 수정되는 경우가 있어 주간 보완 크롤링 추가
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaxScheduleCrawlingScheduler {

    private final TaxScheduleCrawler crawler;
    private final TaxScheduleRepository repository;

    /**
     * 매월 1일 새벽 2시 - 정기 크롤링
     * 현재 월 기준 3개월치 수집
     */
    @Scheduled(cron = "0 0 2 1 * *")
    public void monthlyFullCrawl() {
        log.info("===== [정기 크롤링 시작] {} =====", LocalDateTime.now());
        try {
            crawler.crawlNextThreeMonths();
            log.info("===== [정기 크롤링 완료] =====");
        } catch (Exception e) {
            log.error("[정기 크롤링 실패]: {}", e.getMessage(), e);
            // TODO: 슬랙/이메일 알림 연동
        }
    }

    /**
     * 매주 월요일 새벽 3시 - 보완 크롤링
     * 국세청 일정 수정사항 반영
     */
    @Scheduled(cron = "0 0 3 * * MON")
    public void weeklySupplementCrawl() {
        log.info("===== [주간 보완 크롤링 시작] {} =====", LocalDateTime.now());
        try {
            crawler.crawlNextThreeMonths();
            log.info("===== [주간 보완 크롤링 완료] =====");
        } catch (Exception e) {
            log.error("[주간 보완 크롤링 실패]: {}", e.getMessage(), e);
        }
    }

    /**
     * 매일 새벽 4시 - 오래된 데이터 정리
     * 6개월 이전 데이터 삭제로 DB 용량 관리
     */
    @Transactional
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanupOldData() {
        LocalDate cutoffDate = LocalDate.now().minusMonths(6);
        int deleted = repository.deleteOldSchedules(cutoffDate);
        log.info("[데이터 정리] {} 이전 데이터 {}건 삭제", cutoffDate, deleted);
    }
}
