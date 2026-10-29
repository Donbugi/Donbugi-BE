package hongik.finEdu.quiz.daily;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import hongik.finEdu.points.policy.PointPolicy;
import hongik.finEdu.points.service.PointRewardService;
import hongik.finEdu.quiz.daily.dto.QuizAnswerResponse;
import hongik.finEdu.quiz.dto.QuizResponseDto;
import hongik.finEdu.quiz.service.QuizService;
import hongik.finEdu.quiz.stats.dto.QuizAttemptRequest;
import hongik.finEdu.quiz.stats.service.QuizStatsRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 기사 상세 퀴즈 서버 채점 + 참여 보상(기사당 1회).
 * 기존 /api/points/rewards/news-detail-quiz 는 퀴즈를 풀지 않아도 articleId 만 바꿔 호출하면 지급됐다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleQuizAnswerService {

    private final QuizService quizService;
    private final PointRewardService pointRewardService;
    private final QuizStatsRedisService quizStatsRedisService;

    public QuizAnswerResponse answer(String userId, Long articleId, int questionIndex, int selectedIndex) {
        if (articleId == null || articleId < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "articleId는 필수입니다.");
        }
        List<QuizResponseDto> quizzes = quizService.getQuiz(articleId);
        if (questionIndex < 0 || questionIndex >= quizzes.size()) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "questionIndex=" + questionIndex);
        }
        QuizResponseDto q = quizzes.get(questionIndex);
        if (q.getOptions() == null || selectedIndex < 0 || selectedIndex >= q.getOptions().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "selectedIndex 범위를 벗어났습니다.");
        }
        boolean correct = q.getCorrectIndex() == selectedIndex;

        try {
            quizStatsRedisService.recordAttempt(new QuizAttemptRequest(
                    userId, correct, q.getQuestion(),
                    q.getOptions().get(selectedIndex),
                    q.getOptions().get(q.getCorrectIndex()),
                    q.getExplanation()));
        } catch (Exception e) {
            log.warn("[article-quiz] 통계 기록 실패 userId={}: {}", userId, e.getMessage());
        }

        // 기존 claim 키 형식 유지 → 이미 받은 사용자는 중복 지급되지 않음
        PointAwardResult r = pointRewardService.tryAwardOnce(
                userId,
                "newsdetail-quiz:" + userId + ":" + articleId,
                PointPolicy.NEWS_DETAIL_QUIZ_PARTICIPATE,
                "뉴스 퀴즈 참여 (+" + PointPolicy.NEWS_DETAIL_QUIZ_PARTICIPATE + "P)",
                "기사 #" + articleId);

        return new QuizAnswerResponse(articleId, questionIndex, correct, q.getCorrectIndex(), q.getExplanation(),
                r.awarded(), r.awarded() ? r.amount() : 0, r.balanceAfter());
    }
}
