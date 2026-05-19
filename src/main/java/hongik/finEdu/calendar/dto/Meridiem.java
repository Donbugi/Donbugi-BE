package hongik.finEdu.calendar.dto;

/**
 * 사용자 입력 12시간제 — 오전/오후.
 */
public enum Meridiem {
    AM,
    PM;

    public static Meridiem fromClientString(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("오전/오후(meridiem)는 필수입니다.");
        }
        return switch (value.trim()) {
            case "AM", "am", "오전" -> AM;
            case "PM", "pm", "오후" -> PM;
            default -> throw new IllegalArgumentException(
                    "meridiem은 AM, PM, 오전, 오후 중 하나여야 합니다.");
        };
    }
}
