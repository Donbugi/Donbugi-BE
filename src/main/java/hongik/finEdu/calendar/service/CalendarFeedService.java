package hongik.finEdu.calendar.service;

import hongik.finEdu.calendar.dto.CalendarEventItemDto;
import hongik.finEdu.calendar.dto.CalendarEventSource;
import hongik.finEdu.calendar.dto.CalendarMonthResponse;
import hongik.finEdu.calendar.dto.Meridiem;
import hongik.finEdu.calendar.dto.UserCalendarEventResponseDto;
import hongik.finEdu.calendar.entity.IssueSchedule;
import hongik.finEdu.calendar.entity.TaxSchedule;
import hongik.finEdu.calendar.repository.TaxScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CalendarFeedService {

    private final IssueScheduleService issueScheduleService;
    private final TaxScheduleRepository taxScheduleRepository;
    private final UserCalendarEventService userCalendarEventService;

    @Transactional(readOnly = true)
    public CalendarMonthResponse getMonth(String userId, int year, int month) {
        LocalDate from = LocalDate.of(year, month, 1);
        LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
        List<CalendarEventItemDto> events = new ArrayList<>();
        events.addAll(mapIssue(issueScheduleService.firstPerCategoryPerDayForMonth(year, month)));
        events.addAll(mapTax(taxScheduleRepository.findByScheduleDateBetweenOrderByScheduleDateAsc(from, to)));
        events.addAll(mapUser(userCalendarEventService.listByRange(userId, from, to)));
        events.sort(Comparator.comparing(CalendarEventItemDto::date).thenComparing(CalendarEventItemDto::timeLabel));
        return new CalendarMonthResponse(year, month, events);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventItemDto> getToday(String userId) {
        LocalDate today = LocalDate.now();
        return getMonth(userId, today.getYear(), today.getMonthValue()).events().stream()
                .filter(e -> e.date().equals(today))
                .toList();
    }

    private List<CalendarEventItemDto> mapIssue(List<IssueSchedule> rows) {
        return rows.stream()
                .map(s -> new CalendarEventItemDto(
                        "issue-" + s.getId(),
                        CalendarEventSource.ISSUE,
                        s.getTitle(),
                        s.getScheduleDate(),
                        "",
                        s.getCategory(),
                        colorForCategory(s.getCategory())))
                .toList();
    }

    private List<CalendarEventItemDto> mapTax(List<TaxSchedule> rows) {
        return rows.stream()
                .map(s -> new CalendarEventItemDto(
                        "tax-" + s.getId(),
                        CalendarEventSource.TAX,
                        s.getTitle(),
                        s.getScheduleDate(),
                        "",
                        "세무 일정",
                        "#3CBBA2"))
                .toList();
    }

    private List<CalendarEventItemDto> mapUser(List<UserCalendarEventResponseDto> rows) {
        return rows.stream()
                .map(u -> {
                    LocalDate date = LocalDate.of(u.year(), u.month(), u.day());
                    String merLabel = u.meridiem() == Meridiem.AM ? "오전" : "오후";
                    return new CalendarEventItemDto(
                            "user-" + u.id(),
                            CalendarEventSource.USER,
                            u.title(),
                            date,
                            merLabel + " " + u.hour() + ":" + String.format("%02d", u.minute()),
                            u.memo(),
                            "#7C3AED");
                })
                .toList();
    }

    private static String colorForCategory(String category) {
        if (category == null) {
            return "#7C3AED";
        }
        return switch (category) {
            case "금리", "환율" -> "#FF9F1C";
            case "물가", "고용" -> "#FF4D6D";
            case "실적", "기업" -> "#3CBBA2";
            default -> "#7C3AED";
        };
    }
}
