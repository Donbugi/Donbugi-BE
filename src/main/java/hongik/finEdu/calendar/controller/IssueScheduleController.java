package hongik.finEdu.calendar.controller;

import hongik.finEdu.calendar.entity.IssueSchedule;
import hongik.finEdu.calendar.service.IssueScheduleService;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = OpenApiTags.CALENDAR, description = "삼성증권 일간이슈")
@RestController
@RequestMapping("/api/issue-schedule")
@RequiredArgsConstructor
public class IssueScheduleController {

    private final IssueScheduleService issueScheduleService;

    @Operation(summary = "일간이슈 (연월)", description = "일자×category당 첫 1건. FOMC·CPI 등. 인증 불필요.")
    @GetMapping
    public ResponseEntity<List<IssueSchedule>> byYearMonth(
            @OpenApiParams.YearQuery @RequestParam int year,
            @OpenApiParams.MonthQuery @RequestParam int month) {
        return ResponseEntity.ok(issueScheduleService.firstPerCategoryPerDayForMonth(year, month));
    }

    @Operation(summary = "일간이슈 import (관리)", description = "삼성증권 API 크롤 후 DB 적재. savedCount=신규 건수.")
    @PostMapping("/import")
    public ResponseEntity<ImportResult> importMonth(
            @OpenApiParams.YearQuery @RequestParam int year,
            @OpenApiParams.MonthQuery @RequestParam int month) {
        int saved = issueScheduleService.importMonth(year, month);
        return ResponseEntity.ok(new ImportResult(saved));
    }

    /** 이번 요청에서 새로 넣은 행 수(이미 DB에 있던 일자·seq는 포함 안 함). */
    public record ImportResult(int savedCount) {}
}
