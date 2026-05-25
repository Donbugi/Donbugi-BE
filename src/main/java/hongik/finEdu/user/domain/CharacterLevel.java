package hongik.finEdu.user.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CharacterLevel {

    CHICK(1, "🐥", "병아리", "금융 입문 단계"),
    SQUIRREL(2, "🐿️", "다람쥐", "정보 수집 단계"),
    FOX(3, "🦊", "여우", "분석 단계"),
    LION(4, "🦁", "사자", "전략 단계"),
    DRAGON(5, "🐉", "용", "금융 전문가 단계");

    private final int level;
    private final String emoji;
    private final String name;
    private final String tag;

    public static CharacterLevel fromTotalScore(int total) {
        if (total <= 7) {
            return CHICK;
        }
        if (total <= 10) {
            return SQUIRREL;
        }
        if (total <= 13) {
            return FOX;
        }
        if (total <= 16) {
            return LION;
        }
        return DRAGON;
    }

    public static CharacterLevel fromLevel(int level) {
        for (CharacterLevel c : values()) {
            if (c.level == level) {
                return c;
            }
        }
        return CHICK;
    }
}
