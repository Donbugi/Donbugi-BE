package hongik.finEdu.auth.dto;

import java.util.List;

/** FE 온보딩 4문항 점수 (각 1, 2, 3, 5 중 하나) */
public record OnboardingSubmitRequest(List<Integer> scores) {
}
