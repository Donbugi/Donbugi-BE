package hongik.finEdu.calendar.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.calendar.dto.UserCalendarEventCreateRequest;
import hongik.finEdu.calendar.dto.UserCalendarEventResponseDto;
import hongik.finEdu.calendar.dto.UserCalendarEventUpdateRequest;
import hongik.finEdu.calendar.service.UserCalendarEventService;
import hongik.finEdu.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-calendar-events")
@RequiredArgsConstructor
public class UserCalendarEventController {

    private final UserCalendarEventService userCalendarEventService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping
    public ResponseEntity<UserCalendarEventResponseDto> create(
            Authentication authentication,
            @RequestBody UserCalendarEventCreateRequest request) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.create(userId, request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PatchMapping("/{eventId}")
    public ResponseEntity<UserCalendarEventResponseDto> update(
            Authentication authentication,
            @PathVariable Long eventId,
            @RequestBody UserCalendarEventUpdateRequest request) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.update(userId, eventId, request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @DeleteMapping("/{eventId}")
    public ResponseEntity<Map<String, String>> delete(
            Authentication authentication,
            @PathVariable Long eventId) {
        String userId = authUserResolver.requireUserId(authentication);
        userCalendarEventService.delete(userId, eventId);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<List<UserCalendarEventResponseDto>> byYearMonth(
            Authentication authentication,
            @RequestParam int year,
            @RequestParam int month) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.listByYearMonth(userId, year, month));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/range")
    public ResponseEntity<List<UserCalendarEventResponseDto>> byRange(
            Authentication authentication,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(userCalendarEventService.listByRange(userId, from, to));
    }
}
