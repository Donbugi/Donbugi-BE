package hongik.finEdu.calendar.dto;

/**
 * 사용자 정의 캘린더 일정 등록 요청.
 * meridiem: AM, PM 또는 오전, 오후
 */
public record UserCalendarEventCreateRequest(
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
