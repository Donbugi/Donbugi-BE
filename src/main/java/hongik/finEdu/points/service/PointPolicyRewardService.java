package hongik.finEdu.points.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.*;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.policy.PointPolicy;
import hongik.finEdu.points.repository.PointAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PointPolicyRewardService {

    private final PointRewardService pointRewardService;
    private final PointAccountRepository accountRepository;

    @Value("${app.points.timezone:Asia/Seoul}")
    private String timezoneId;

    @Transactional
    public DailyQuizRewardResponse rewardDailyQuiz(DailyQuizRewardRequest request) {
        String userId = requireUserId(request.userId());
        String sessionId = requireSessionId(request.sessionId());
        List<DailyQuizSlotResult> results = request.results();
        if (results == null || results.size() != PointPolicy.DAILY_QUIZ_SLOT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "오늘의 과제는 슬롯 " + PointPolicy.DAILY_QUIZ_SLOT_COUNT + "개를 보내야 합니다.");
        }
        Set<Integer> seen = new HashSet<>();
        for (DailyQuizSlotResult s : results) {
            if (s.order() < 1 || s.order() > PointPolicy.DAILY_QUIZ_SLOT_COUNT) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "order는 1~" + PointPolicy.DAILY_QUIZ_SLOT_COUNT);
            }
            if (!seen.add(s.order())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "order 가 중복되었습니다.");
            }
        }
        if (seen.size() != PointPolicy.DAILY_QUIZ_SLOT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "order 1,2,3 이 모두 필요합니다.");
        }

        LocalDate date = LocalDate.now(ZoneId.of(timezoneId));
        int totalAwarded = 0;
        for (DailyQuizSlotResult slot : results) {
            if (slot.attempted()) {
                String claim = "dailyquiz:" + date + ":" + sessionId + ":slot:" + slot.order();
                PointAwardResult r = pointRewardService.tryAwardOnce(
                        userId,
                        claim,
                        PointPolicy.DAILY_QUIZ_PER_ATTEMPT,
                        "오늘의 과제 퀴즈 참여 (" + slot.order() + "/" + PointPolicy.DAILY_QUIZ_SLOT_COUNT + ")",
                        null);
                if (r.awarded()) {
                    totalAwarded += r.amount();
                }
            }
        }

        boolean allAttempted = results.stream().allMatch(DailyQuizSlotResult::attempted);
        boolean allCorrect = results.stream().allMatch(s -> s.attempted() && s.correct());
        if (allAttempted && allCorrect) {
            String bonusClaim = "dailyquiz:" + date + ":" + sessionId + ":allCorrect";
            PointAwardResult b = pointRewardService.tryAwardOnce(
                    userId,
                    bonusClaim,
                    PointPolicy.DAILY_QUIZ_ALL_CORRECT_BONUS,
                    "오늘의 과제 만점 보너스",
                    null);
            if (b.awarded()) {
                totalAwarded += b.amount();
            }
        }

        int bal = accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
        return new DailyQuizRewardResponse(totalAwarded, bal);
    }

    @Transactional
    public NewsDetailQuizRewardResponse rewardNewsDetailQuiz(NewsDetailQuizRewardRequest request) {
        String userId = requireUserId(request.userId());
        Long articleId = request.articleId();
        if (articleId == null || articleId < 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "articleId는 필수입니다.");
        }
        String claim = "newsdetail-quiz:" + userId + ":" + articleId;
        PointAwardResult r = pointRewardService.tryAwardOnce(
                userId,
                claim,
                PointPolicy.NEWS_DETAIL_QUIZ_PARTICIPATE,
                "뉴스 퀴즈 참여 (+20P)",
                "기사 #" + articleId);
        return new NewsDetailQuizRewardResponse(r.awarded(), r.awarded() ? r.amount() : 0, r.balanceAfter());
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }

    private static String requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sessionId는 필수입니다 (일일 과제 세션 구분).");
        }
        String s = sessionId.trim();
        if (s.length() > 80) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "sessionId는 80자 이내여야 합니다.");
        }
        return s;
    }
}
