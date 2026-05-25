package hongik.finEdu.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "온보딩 4문항 점수 제출")
public record OnboardingSubmitRequest(
        @Schema(
                description = "4문항 점수 배열. 각 값은 1·2·3·5 중 하나",
                example = "[1, 2, 3, 5]",
                minLength = 4,
                maxLength = 4
        )
        List<Integer> scores
) {
}
