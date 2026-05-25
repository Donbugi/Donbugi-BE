package hongik.finEdu.calendar.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.calendar.dto.UserCalendarEventCreateRequest;
import hongik.finEdu.calendar.dto.UserCalendarEventResponseDto;
import hongik.finEdu.calendar.dto.UserCalendarEventUpdateRequest;
import hongik.finEdu.calendar.service.UserCalendarEventService;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = OpenApiTags.CALENDAR, description = "사용자 개인 일정 CRUD")
@RestController
@RequestMapping("/api/user-calendar-events")
@RequiredArgsConstructor
public class UserCalendarEventController {

    private final UserCalendarEventService userCalendarEventService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "일정 등록",
            description = """
                    JWT userId에 귀속. meridiem: AM/PM 또는 오전/오후, hour: 1~12.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping
    public ResponseEntity<UserCalendarEventResponseDto> create(
            Authentication authentication,
            @RequestBody UserCalendarEventCreateRequest request) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.create(userId, request));
    }

    @Operation(summary = "일정 수정", description = "본인 일정만 수정 가능. 타 user 일정 404.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PatchMapping("/{eventId}")
    public ResponseEntity<UserCalendarEventResponseDto> update(
            Authentication authentication,
            @Parameter(description = "일정 ID", example = "1") @PathVariable Long eventId,
            @RequestBody UserCalendarEventUpdateRequest request) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.update(userId, eventId, request));
    }

    @Operation(summary = "일정 삭제")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Map<String, String>> delete(
            Authentication authentication,
            @Parameter(description = "일정 ID", example = "1") @PathVariable Long eventId) {
        String userId = authUserResolver.requireUserId(authentication);
        userCalendarEventService.delete(userId, eventId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @Operation(summary = "월별 내 일정 목록")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<List<UserCalendarEventResponseDto>> byYearMonth(
            Authentication authentication,
            @OpenApiParams.YearQuery @RequestParam int year,
            @OpenApiParams.MonthQuery @RequestParam int month) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.listByYearMonth(userId, year, month));
    }

    @Operation(summary = "기간별 내 일정", description = "from~to inclusive, ISO 날짜 (yyyy-MM-dd)")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/range")
    public ResponseEntity<List<UserCalendarEventResponseDto>> byRange(
            Authentication authentication,
            @Parameter(description = "시작일", example = "2026-05-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일", example = "2026-05-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.listByRange(userId, from, to));
    }
}
