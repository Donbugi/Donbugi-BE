package hongik.finEdu.quiz.daily;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.policy.PointPolicy;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.service.PointRewardService;
import hongik.finEdu.quiz.daily.dto.DailyQuizAnswerResponse;
import hongik.finEdu.quiz.daily.dto.DailyQuizItemView;
import hongik.finEdu.quiz.daily.dto.DailyQuizSessionResponse;
import hongik.finEdu.quiz.dto.QuizSessionItemDto;
import hongik.finEdu.quiz.service.QuizService;
import hongik.finEdu.quiz.stats.dto.QuizAttemptRequest;
import hongik.finEdu.quiz.stats.service.QuizStatsRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 오늘의 과제 — 서버 발급 세션 + 서버 채점.
 *
 * <p>기존 방식의 문제: 클라이언트가 sessionId 를 만들고 정답 여부를 자기신고했기 때문에
 * sessionId 만 바꿔 무한 보상, 응답에 포함된 correctIndex 로 만점 보너스까지 가능했다.</p>
 *
 * <ul>
 *   <li>세션은 (userId, 서울 날짜)당 1개. 문항과 정답은 Redis 에만 저장하고 응답에서 정답을 숨긴다.</li>
 *   <li>답안은 슬롯별 HSETNX 로 1회만 접수 (동시 제출에도 안전).</li>
 *   <li>보상 claim 키는 세션과 무관하게 (userId, date, slot) — 세션 재발급으로 중복 수령 불가.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyQuizSessionService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final QuizService quizService;
    private final PointRewardService pointRewardService;
    private final PointAccountRepository pointAccountRepository;
    private final QuizStatsRedisService quizStatsRedisService;

    @Value("${app.points.timezone:Asia/Seoul}")
    private String timezoneId;

    @Value("${quiz.daily.redis-prefix:finEdu:quiz:daily:}")
    private String prefix;

    private static final Duration TTL = Duration.ofDays(2);

    /** Redis 에 저장되는 세션 원본 (정답 포함) */
    public record StoredSession(String sessionId, LocalDate date, List<QuizSessionItemDto> items) {
    }

    public DailyQuizSessionResponse getOrCreate(String userId) {
        LocalDate today = today();
        StoredSession session = loadOrCreate(userId, today);
        return toResponse(session, answers(userId, today));
    }

    public DailyQuizAnswerResponse answer(String userId, int order, int selectedIndex) {
        LocalDate today = today();
        StoredSession session = load(userId, today);
        if (session == null) {
            throw new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "오늘의 과제를 먼저 조회해 주세요.");
        }
        QuizSessionItemDto item = session.items().stream()
                .filter(i -> i.order() == order)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_NOT_FOUND, "order=" + order));
        if (selectedIndex < 0 || selectedIndex >= item.options().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "selectedIndex 범위를 벗어났습니다.");
        }

        Boolean first = redis.opsForHash().putIfAbsent(answersKey(userId, today), String.valueOf(order), String.valueOf(selectedIndex));
        redis.expire(answersKey(userId, today), TTL);
        if (!Boolean.TRUE.equals(first)) {
            throw new BusinessException(ErrorCode.QUIZ_ALREADY_ANSWERED);
        }

        boolean correct = item.correctIndex() == selectedIndex;
        recordStats(userId, item, selectedIndex, correct);

        PointAwardResult slot = pointRewardService.tryAwardOnce(
                userId,
                "dailyquiz:" + userId + ":" + today + ":slot:" + order,
                PointPolicy.DAILY_QUIZ_PER_ATTEMPT,
                "오늘의 과제 퀴즈 참여 (" + order + "/" + PointPolicy.DAILY_QUIZ_SLOT_COUNT + ")",
                null);

        Map<Integer, Integer> answered = answers(userId, today);
        boolean completed = answered.size() >= session.items().size();
        int bonus = 0;
        if (completed && allCorrect(session, answered)) {
            PointAwardResult b = pointRewardService.tryAwardOnce(
                    userId,
                    "dailyquiz:" + userId + ":" + today + ":allCorrect",
                    PointPolicy.DAILY_QUIZ_ALL_CORRECT_BONUS,
                    "오늘의 과제 만점 보너스",
                    null);
            bonus = b.awarded() ? b.amount() : 0;
        }

        int balance = pointAccountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
        return new DailyQuizAnswerResponse(
                order, correct, item.correctIndex(), item.explanation(),
                slot.awarded() ? slot.amount() : 0, bonus, balance, completed);
    }

    // ───────────────────────── internal ─────────────────────────

    private StoredSession loadOrCreate(String userId, LocalDate date) {
        StoredSession existing = load(userId, date);
        if (existing != null) {
            return existing;
        }
        List<QuizSessionItemDto> items = quizService.getRandomQuizSession(PointPolicy.DAILY_QUIZ_SLOT_COUNT);
        StoredSession created = new StoredSession(UUID.randomUUID().toString(), date, items);
        Boolean ok = redis.opsForValue().setIfAbsent(sessionKey(userId, date), write(created), TTL);
        if (Boolean.TRUE.equals(ok)) {
            return created;
        }
        // 동시 요청이 먼저 만들었으면 그 세션을 사용
        StoredSession raced = load(userId, date);
        if (raced == null) {
            throw new BusinessException(ErrorCode.CONFLICT, "오늘의 과제 세션 생성 충돌");
        }
        return raced;
    }

    private StoredSession load(String userId, LocalDate date) {
        String json = redis.opsForValue().get(sessionKey(userId, date));
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, StoredSession.class);
        } catch (JacksonException e) {
            log.warn("[daily-quiz] 세션 파싱 실패 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    private String write(StoredSession s) {
        try {
            return objectMapper.writeValueAsString(s);
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "세션 직렬화 실패", e);
        }
    }

    private Map<Integer, Integer> answers(String userId, LocalDate date) {
        Map<Object, Object> raw = redis.opsForHash().entries(answersKey(userId, date));
        Map<Integer, Integer> out = new HashMap<>();
        raw.forEach((k, v) -> {
            try {
                out.put(Integer.parseInt(k.toString()), Integer.parseInt(v.toString()));
            } catch (NumberFormatException ignored) {
                // 손상된 필드는 무시
            }
        });
        return out;
    }

    private static boolean allCorrect(StoredSession s, Map<Integer, Integer> answered) {
        for (QuizSessionItemDto i : s.items()) {
            Integer sel = answered.get(i.order());
            if (sel == null || sel != i.correctIndex()) {
                return false;
            }
        }
        return true;
    }

    private DailyQuizSessionResponse toResponse(StoredSession s, Map<Integer, Integer> answered) {
        List<DailyQuizItemView> views = new ArrayList<>();
        int correctCount = 0;
        for (QuizSessionItemDto i : s.items()) {
            Integer sel = answered.get(i.order());
            boolean done = sel != null;
            Boolean correct = done ? sel == i.correctIndex() : null;
            if (Boolean.TRUE.equals(correct)) {
                correctCount++;
            }
            views.add(new DailyQuizItemView(
                    i.order(), i.articleId(), i.articleTitle(), i.question(), i.options(),
                    done, sel, correct,
                    done ? i.correctIndex() : null,
                    done ? i.explanation() : null));
        }
        return new DailyQuizSessionResponse(
                s.sessionId(), s.date(), views, answered.size(), correctCount,
                answered.size() >= s.items().size());
    }

    private void recordStats(String userId, QuizSessionItemDto item, int selected, boolean correct) {
        try {
            quizStatsRedisService.recordAttempt(new QuizAttemptRequest(
                    userId, correct, item.question(),
                    item.options().get(selected),
                    item.options().get(item.correctIndex()),
                    item.explanation()));
        } catch (Exception e) {
            log.warn("[daily-quiz] 통계 기록 실패 userId={}: {}", userId, e.getMessage());
        }
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(timezoneId));
    }

    private String sessionKey(String userId, LocalDate date) {
        return prefix + userId + ":" + date;
    }

    private String answersKey(String userId, LocalDate date) {
        return prefix + userId + ":" + date + ":answers";
    }
}
