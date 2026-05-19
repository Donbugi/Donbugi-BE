package hongik.finEdu.quiz.stats.controller;

import hongik.finEdu.quiz.stats.dto.QuizAttemptRequest;
import hongik.finEdu.quiz.stats.dto.QuizStatsDashboardResponse;
import hongik.finEdu.quiz.stats.service.QuizStatsRedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz/stats")
@RequiredArgsConstructor
public class QuizStatsController {

    private final QuizStatsRedisService quizStatsRedisService;

    /**
     * 퀴즈 1문항 결과를 Redis에 반영한다. (정답: 일/월 c++, 오답: w++ 및 오답 ZSET 추가)
     */
    @PostMapping("/attempt")
    public ResponseEntity<Void> recordAttempt(@RequestBody QuizAttemptRequest request) {
        quizStatsRedisService.recordAttempt(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 대시보드: 7일(같은 요일 기준 8일치 일합산) 정답률, 이번 달 정답률, 해당 구간 오답 노트
     */
    @GetMapping("/dashboard")
    public ResponseEntity<QuizStatsDashboardResponse> dashboard(@RequestParam String userId) {
        return ResponseEntity.ok(quizStatsRedisService.getDashboard(userId));
    }
}
