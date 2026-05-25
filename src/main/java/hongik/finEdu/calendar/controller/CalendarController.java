package hongik.finEdu.calendar.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.calendar.dto.CalendarEventItemDto;
import hongik.finEdu.calendar.dto.CalendarMonthResponse;
import hongik.finEdu.calendar.service.CalendarFeedService;
import hongik.finEdu.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarFeedService calendarFeedService;
    private final AuthUserResolver authUserResolver;

    /** issue + tax + user 일정 통합 월별 조회 */
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/month")
    public ResponseEntity<CalendarMonthResponse> month(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(calendarFeedService.getMonth(userId, year, month));
    }

    /** 홈 D-DAY — 오늘 일정 */
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/today")
    public ResponseEntity<List<CalendarEventItemDto>> today(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(calendarFeedService.getToday(userId));
    }
}
