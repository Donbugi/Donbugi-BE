package hongik.finEdu.calendar.dto;

/**
 * 사용자 일정 수정 요청.
 */
public record UserCalendarEventUpdateRequest(
        String title,
        int year,
        int month,
        int day,
        String meridiem,
        int hour,
        int minute,
        String memo
) {
}
