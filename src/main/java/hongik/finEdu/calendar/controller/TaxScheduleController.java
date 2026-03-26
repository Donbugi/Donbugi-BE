package hongik.finEdu.calendar.controller;

import hongik.finEdu.calendar.entity.TaxSchedule;
import hongik.finEdu.calendar.repository.TaxScheduleRepository;
import hongik.finEdu.calendar.service.TaxScheduleCrawler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/tax-schedule")
@RequiredArgsConstructor
public class TaxScheduleController {

    private final TaxScheduleRepository repository;
    private final TaxScheduleCrawler crawler;

    /**
     * 특정 연월 세무일정 조회
     * GET /api/tax-schedule?year=2026&month=3
     */
    @GetMapping
    public ResponseEntity<List<TaxSchedule>> getSchedule(
        @RequestParam int year,
        @RequestParam int month
    ) {
        List<TaxSchedule> schedules = repository.findByYearAndMonthOrderByDayAsc(year, month);
        return ResponseEntity.ok(schedules);
    }

    /**
     * 날짜 범위 조회 (캘린더 뷰용)
     * GET /api/tax-schedule/range?from=2026-03-01&to=2026-05-31
     */
    @GetMapping("/range")
    public ResponseEntity<List<TaxSchedule>> getScheduleByRange(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        List<TaxSchedule> schedules =
            repository.findByScheduleDateBetweenOrderByScheduleDateAsc(from, to);
        return ResponseEntity.ok(schedules);
    }

    /**
     * 수동 크롤링 트리거 (관리자용)
     * POST /api/tax-schedule/crawl
     * - 배포 직후 즉시 데이터 수집할 때 사용
     */
    @PostMapping("/crawl")
    public ResponseEntity<Map<String, String>> triggerCrawl() {
        try {
            crawler.crawlNextThreeMonths();
            return ResponseEntity.ok(Map.of("status", "success", "message", "크롤링 완료"));
        } catch (Exception e) {
            log.error("수동 크롤링 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    /**
     * 특정 월만 수동 크롤링 (관리자용)
     * POST /api/tax-schedule/crawl/2026/3
     */
    @PostMapping("/crawl/{year}/{month}")
    public ResponseEntity<Map<String, String>> triggerCrawlMonth(
        @PathVariable int year,
        @PathVariable int month
    ) {
        try {
            crawler.crawlMonth(year, month);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", year + "년 " + month + "월 크롤링 완료"
            ));
        } catch (IOException e) {
            log.error("수동 크롤링 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
