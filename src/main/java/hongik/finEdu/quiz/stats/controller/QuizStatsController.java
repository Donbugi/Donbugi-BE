package hongik.finEdu.quiz.stats.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.quiz.stats.dto.QuizAttemptRequest;
import hongik.finEdu.quiz.stats.dto.QuizStatsDashboardResponse;
import hongik.finEdu.quiz.stats.service.QuizStatsRedisService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz/stats")
@RequiredArgsConstructor
public class QuizStatsController {

    private final QuizStatsRedisService quizStatsRedisService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/attempt")
    public ResponseEntity<Void> recordAttempt(
            Authentication authentication,
            @RequestBody QuizAttemptRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        quizStatsRedisService.recordAttempt(request);
        return ResponseEntity.noContent().build();
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/dashboard")
    public ResponseEntity<QuizStatsDashboardResponse> dashboard(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(quizStatsRedisService.getDashboard(uid));
    }
}
