package hongik.finEdu.quiz.stats.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.quiz.stats.dto.QuizAttemptRequest;
import hongik.finEdu.quiz.stats.dto.QuizStatsDashboardResponse;
import hongik.finEdu.quiz.stats.service.QuizStatsRedisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = OpenApiTags.QUIZ, description = "퀴즈 통계·오답노트")
@RestController
@RequestMapping("/api/quiz/stats")
@RequiredArgsConstructor
public class QuizStatsController {

    private final QuizStatsRedisService quizStatsRedisService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "퀴즈 1문항 결과 기록",
            description = "Redis에 정답/오답 반영. 오답 시 question·userAnswer·correctAnswer 필수.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/attempt")
    public ResponseEntity<Void> recordAttempt(
            Authentication authentication,
            @RequestBody QuizAttemptRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        quizStatsRedisService.recordAttempt(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "퀴즈 대시보드",
            description = """
                    마이페이지용.
                    - rollingWeek: 최근 7일(동일 요일 기준) 정답률
                    - thisMonth: 이번 달 정답률
                    - wrongNotesInWindow: 7일 구간 오답노트""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/dashboard")
    public ResponseEntity<QuizStatsDashboardResponse> dashboard(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(quizStatsRedisService.getDashboard(uid));
    }
}
