package hongik.finEdu.calendar.controller;

import hongik.finEdu.calendar.dto.UserCalendarEventCreateRequest;
import hongik.finEdu.calendar.dto.UserCalendarEventResponseDto;
import hongik.finEdu.calendar.service.UserCalendarEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/user-calendar-events")
@RequiredArgsConstructor
public class UserCalendarEventController {

    private final UserCalendarEventService userCalendarEventService;

    /**
     * 사용자 일정 등록 (제목, 년·월·일, 오전/오후·시·분, 선택 메모)
     * POST /api/user-calendar-events
     */
    @PostMapping
    public ResponseEntity<UserCalendarEventResponseDto> create(
            @RequestBody UserCalendarEventCreateRequest request) {
        return ResponseEntity.ok(userCalendarEventService.create(request));
    }

    /**
     * 해당 월의 사용자 일정 목록
     * GET /api/user-calendar-events?year=2026&month=5
     */
    @GetMapping
    public ResponseEntity<List<UserCalendarEventResponseDto>> byYearMonth(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(userCalendarEventService.listByYearMonth(year, month));
    }

    /**
     * 기간 내 사용자 일정 (캘린더 뷰)
     * GET /api/user-calendar-events/range?from=2026-05-01&to=2026-05-31
     */
    @GetMapping("/range")
    public ResponseEntity<List<UserCalendarEventResponseDto>> byRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(userCalendarEventService.listByRange(from, to));
    }
}
