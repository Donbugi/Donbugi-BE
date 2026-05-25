package hongik.finEdu.user.domain;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FinIqLevelPolicy {

    private static final int[] THRESHOLDS = {0, 1000, 2500, 4000, 5000};

    public record FinIqProgress(int finIqBalance, int currentLevel, int pointsToNextLevel, int progressPercent) {}

    public static FinIqProgress progress(int balance) {
        int b = Math.max(0, balance);
        int level = 1;
        for (int i = THRESHOLDS.length - 1; i >= 0; i--) {
            if (b >= THRESHOLDS[i]) {
                level = i + 1;
                break;
            }
        }
        int nextThreshold = level >= THRESHOLDS.length ? THRESHOLDS[THRESHOLDS.length - 1] : THRESHOLDS[level];
        int prevThreshold = THRESHOLDS[level - 1];
        int toNext = level >= THRESHOLDS.length ? 0 : Math.max(0, nextThreshold - b);
        int span = nextThreshold - prevThreshold;
        int progress = level >= THRESHOLDS.length ? 100
                : (span <= 0 ? 0 : Math.min(100, (b - prevThreshold) * 100 / span));
        return new FinIqProgress(b, level, toNext, progress);
    }
}
