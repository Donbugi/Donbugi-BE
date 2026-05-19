package hongik.finEdu.points.dto.policy;

/** 오늘의 과제(일일 퀴즈) 1슬롯 — order 1~3 */
public record DailyQuizSlotResult(int order, boolean attempted, boolean correct) {
}
