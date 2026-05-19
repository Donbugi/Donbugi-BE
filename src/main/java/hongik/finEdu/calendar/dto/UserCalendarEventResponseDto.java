package hongik.finEdu.calendar.dto;

/**
 * 캘린더 표시용 — 입력과 동일한 오전/오후·시(1~12)·분을 유지해 반환.
 */
public record UserCalendarEventResponseDto(
        Long id,
        String title,
        int year,
        int month,
        int day,
        Meridiem meridiem,
        int hour,
        int minute,
        String memo
) {
}
