package hongik.finEdu.auth.controller;

import hongik.finEdu.auth.dto.OnboardingSubmitRequest;
import hongik.finEdu.auth.dto.OnboardingSubmitResponse;
import hongik.finEdu.auth.service.OnboardingService;
import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping
    public ResponseEntity<OnboardingSubmitResponse> submit(
            Authentication authentication,
            @RequestBody OnboardingSubmitRequest request) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(onboardingService.submit(userId, request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<OnboardingSubmitResponse> result(Authentication authentication) {
        String userId = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(onboardingService.getResult(userId));
    }
}
