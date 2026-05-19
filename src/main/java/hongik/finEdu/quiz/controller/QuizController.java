package hongik.finEdu.quiz.controller;

import hongik.finEdu.quiz.dto.QuizResponseDto;
import hongik.finEdu.quiz.dto.QuizSessionItemDto;
import hongik.finEdu.quiz.dto.RandomQuizPackItemDto;
import hongik.finEdu.quiz.service.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;

    @GetMapping("/random-session")
    public ResponseEntity<List<QuizSessionItemDto>> getRandomQuizSession(
            @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok(quizService.getRandomQuizSession(size));
    }

    /**
     * 무작위 기사 N개에 대한 퀴즈 팩 (기본 3개)
     * GET /api/quiz/random?size=3
     */
    @GetMapping("/random")
    public ResponseEntity<List<RandomQuizPackItemDto>> getRandomQuizPack(
            @RequestParam(defaultValue = "3") int size) {
        return ResponseEntity.ok(quizService.getRandomQuizPack(size));
    }

    /**
     * 기사별 퀴즈 조회
     * GET /api/quiz/{articleId}
     */
    @GetMapping("/{articleId}")
    public ResponseEntity<List<QuizResponseDto>> getQuiz(@PathVariable Long articleId) {
        List<QuizResponseDto> quizzes = quizService.getQuiz(articleId);
        return ResponseEntity.ok(quizzes);
    }
}
