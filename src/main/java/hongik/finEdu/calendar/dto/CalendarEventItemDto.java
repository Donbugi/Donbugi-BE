package hongik.finEdu.calendar.dto;

import java.time.LocalDate;

public record CalendarEventItemDto(
        String id,
        CalendarEventSource source,
        String title,
        LocalDate date,
        String timeLabel,
        String description,
        String color
) {
}
