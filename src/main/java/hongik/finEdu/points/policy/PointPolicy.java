package hongik.finEdu.points.policy;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PointPolicy {
    /** 연속 출석 7의 배수일마다 (7, 14, 21…) */
    public static final int ATTENDANCE_STREAK_REWARD = 100;
    public static final int ATTENDANCE_STREAK_LENGTH = 7;

    /** 오늘 읽은 뉴스 N회마다 (5, 10, 15…) */
    public static final int NEWS_READ_MILESTONE_INTERVAL = 5;
    public static final int NEWS_READ_MILESTONE_POINTS = 20;

    /** 오늘의 과제: 슬롯당 풀기만 해도 */
    public static final int DAILY_QUIZ_PER_ATTEMPT = 20;
    /** 3문항 모두 참여 + 전부 정답 */
    public static final int DAILY_QUIZ_ALL_CORRECT_BONUS = 20;

    /** 뉴스 상세 퀴즈 참여 1회(기사당 1회) */
    public static final int NEWS_DETAIL_QUIZ_PARTICIPATE = 20;

    public static final int DAILY_QUIZ_SLOT_COUNT = 3;
}
