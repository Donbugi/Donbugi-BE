package hongik.finEdu.calendar.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.calendar.dto.CalendarEventItemDto;
import hongik.finEdu.calendar.dto.CalendarMonthResponse;
import hongik.finEdu.calendar.service.CalendarFeedService;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = OpenApiTags.CALENDAR, description = "통합 금융 캘린더")
@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarFeedService calendarFeedService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "월별 통합 일정",
            description = """
                    삼성증권 일간이슈(issue) + 세무일정(tax) + 내 일정(user)을 한 번에 반환.
                    - source: ISSUE | TAX | USER
                    - color: 캘린더 dot 색상""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/month")
    public ResponseEntity<CalendarMonthResponse> month(
            Authentication authentication,
            @OpenApiParams.YearQuery @RequestParam int year,
            @OpenApiParams.MonthQuery @RequestParam int month) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(calendarFeedService.getMonth(userId, year, month));
    }

    @Operation(
            summary = "오늘 일정 (D-DAY)",
            description = "홈 화면 '오늘의 주요 일정' / 알림 seed용. 오늘 날짜 일정만 필터.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/today")
    public ResponseEntity<List<CalendarEventItemDto>> today(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(calendarFeedService.getToday(userId));
    }
}
