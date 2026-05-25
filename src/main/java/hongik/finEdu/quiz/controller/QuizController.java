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
            summary = "오늘의 과제 (FE용 세션)",
            description = """
                    홈 '오늘의 과제' 3문항용. 객관식 1문항 형식 + correctIndex 포함.
                    size 기본 3. AI 서버에서 랜덤 기사 기반 생성.""")
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
