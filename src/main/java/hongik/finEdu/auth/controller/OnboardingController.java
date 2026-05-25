package hongik.finEdu.auth.controller;

import hongik.finEdu.auth.dto.OnboardingSubmitRequest;
import hongik.finEdu.auth.dto.OnboardingSubmitResponse;
import hongik.finEdu.auth.service.OnboardingService;
import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiTags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = OpenApiTags.AUTH)
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "온보딩 제출",
            description = """
                    FE 온보딩 4문항 점수를 제출하고 캐릭터 레벨을 저장합니다.
                    - scores: 길이 4, 각 값은 1·2·3·5 중 하나
                    - 합계 점수 → Lv.1 병아리 ~ Lv.5 용
                    - 1회만 제출 가능 (재제출 시 409 U005)""")
    @ApiResponse(responseCode = "200", description = "캐릭터 레벨·이름·이모지 반환")
    @ApiResponse(responseCode = "409", description = "이미 온보딩 완료")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping
    public ResponseEntity<OnboardingSubmitResponse> submit(
            Authentication authentication,
            @RequestBody OnboardingSubmitRequest request) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(onboardingService.submit(userId, request));
    }

    @Operation(
            summary = "온보딩 결과 조회",
            description = "저장된 캐릭터 레벨·이름·이모지를 조회합니다. 미완료 시 400 U006.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<OnboardingSubmitResponse> result(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(onboardingService.getResult(userId));
    }
}
