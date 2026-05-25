package hongik.finEdu.calendar.dto;

import java.util.List;

public record CalendarMonthResponse(int year, int month, List<CalendarEventItemDto> events) {
}
