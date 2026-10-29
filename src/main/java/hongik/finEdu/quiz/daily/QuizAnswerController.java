package hongik.finEdu.quiz.daily;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.quiz.daily.dto.DailyQuizAnswerRequest;
import hongik.finEdu.quiz.daily.dto.DailyQuizAnswerResponse;
import hongik.finEdu.quiz.daily.dto.DailyQuizSessionResponse;
import hongik.finEdu.quiz.daily.dto.QuizAnswerRequest;
import hongik.finEdu.quiz.daily.dto.QuizAnswerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = OpenApiTags.QUIZ)
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizAnswerController {

    private final DailyQuizSessionService dailyQuizSessionService;
    private final ArticleQuizAnswerService articleQuizAnswerService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "오늘의 과제 세션 조회/발급",
            description = """
                    사용자·날짜(서울)당 1개 세션을 서버가 발급. 같은 날 재호출하면 같은 문항이 반환된다.
                    정답·해설은 답안 제출 후에만 포함된다.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/daily")
    public ResponseEntity<DailyQuizSessionResponse> daily(Authentication authentication) {
        String uid = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(dailyQuizSessionService.getOrCreate(uid));
    }

    @Operation(
            summary = "오늘의 과제 답안 제출 (서버 채점)",
            description = """
                    슬롯당 1회만 제출 가능(재제출 409 Q002). 참여 +20P, 3문항 모두 정답이면 만점 보너스 +20P.
                    보상은 (사용자, 날짜, 슬롯) 단위로 1회.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/daily/answer")
    public ResponseEntity<DailyQuizAnswerResponse> answerDaily(
            Authentication authentication,
            @Valid @RequestBody DailyQuizAnswerRequest request) {
        String uid = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(dailyQuizSessionService.answer(uid, request.order(), request.selectedIndex()));
    }

    @Operation(
            summary = "기사 상세 퀴즈 답안 제출 (서버 채점)",
            description = "정답 여부와 해설을 반환하고, 기사당 1회 참여 보상(+20P)을 지급한다.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/{articleId}/answer")
    public ResponseEntity<QuizAnswerResponse> answerArticle(
            Authentication authentication,
            @PathVariable Long articleId,
            @Valid @RequestBody QuizAnswerRequest request) {
        String uid = authUserResolver.requireUserId(authentication);
        int qi = request.questionIndex() == null ? 0 : request.questionIndex();
        return ResponseEntity.ok(articleQuizAnswerService.answer(uid, articleId, qi, request.selectedIndex()));
    }
}
