package hongik.finEdu.quiz.controller;

import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.quiz.dto.QuizResponseDto;
import hongik.finEdu.quiz.dto.QuizSessionItemDto;
import hongik.finEdu.quiz.dto.RandomQuizPackItemDto;
import hongik.finEdu.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = OpenApiTags.QUIZ)
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @Operation(
            summary = "연습용 랜덤 퀴즈 (보상 없음)",
            description = """
                    연습 모드용. 객관식 1문항 형식 + correctIndex 포함. 포인트 보상과 무관하다.
                    보상이 걸린 '오늘의 과제'는 GET /api/quiz/daily (서버 채점)를 사용한다.
                    size 기본 3. DB에 저장된 퀴즈만 무작위 반환(실시간 AI 호출 없음).""")
    @GetMapping("/random-session")
    public ResponseEntity<List<QuizSessionItemDto>> getRandomQuizSession(
            @Parameter(description = "문항 수", example = "3")
            @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok(quizService.getRandomQuizSession(size));
    }

    @Operation(summary = "랜덤 퀴즈 팩", description = "무작위 기사 N개에 대한 퀴즈 묶음. size 기본 3.")
    @GetMapping("/random")
    public ResponseEntity<List<RandomQuizPackItemDto>> getRandomQuizPack(
            @Parameter(description = "기사/퀴즈 수", example = "3")
            @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok(quizService.getRandomQuizPack(size));
    }

    @Operation(summary = "기사별 퀴즈", description = "뉴스 상세 화면 퀴즈. articleId 필수.")
    @GetMapping("/{articleId}")
    public ResponseEntity<List<QuizResponseDto>> getQuiz(
            @Parameter(description = "기사 ID", example = "42")
            @PathVariable Long articleId) {
        return ResponseEntity.ok(quizService.getQuiz(articleId));
    }
}
