package hongik.finEdu.calendar.controller;

import hongik.finEdu.calendar.entity.IssueSchedule;
import hongik.finEdu.calendar.service.IssueScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 삼성증권 일간이슈: 월 단위 import 후, GET 은 (일자 × category) 당 첫 한 건만 반환.
 */
@RestController
@RequestMapping("/api/issue-schedule")
@RequiredArgsConstructor
public class IssueScheduleController {

    private final IssueScheduleService issueScheduleService;

    /**
     * 해당 월에서 매일마다 분류(category)별 첫 한 건만 — GET /api/issue-schedule?year=2026&month=4
     */
    @GetMapping
    public ResponseEntity<List<IssueSchedule>> byYearMonth(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(issueScheduleService.firstPerCategoryPerDayForMonth(year, month));
    }

    /**
     * 해당 연·월 일별 API를 합쳐, DB에 없는 일자·seq 조합만 추가(이미 있으면 스킵).
     * POST /api/issue-schedule/import?year=2026&month=4
     */
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importMonth(
            @RequestParam int year,
            @RequestParam int month) {
        int saved = issueScheduleService.importMonth(year, month);
        return ResponseEntity.ok(new ImportResult(saved));
    }

    /** 이번 요청에서 새로 넣은 행 수(이미 DB에 있던 일자·seq는 포함 안 함). */
    public record ImportResult(int savedCount) {}
}
