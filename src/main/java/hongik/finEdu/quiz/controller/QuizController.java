package hongik.finEdu.quiz.controller;

import hongik.finEdu.quiz.dto.QuizResponseDto;
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
