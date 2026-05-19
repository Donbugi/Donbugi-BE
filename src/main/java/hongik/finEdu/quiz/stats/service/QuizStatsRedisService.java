package hongik.finEdu.quiz.stats.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.quiz.stats.dto.QuizAttemptRequest;
import hongik.finEdu.quiz.stats.dto.QuizRateSummaryDto;
import hongik.finEdu.quiz.stats.dto.QuizStatsDashboardResponse;
import hongik.finEdu.quiz.stats.dto.QuizWrongNoteDto;
import hongik.finEdu.quiz.stats.model.QuizWrongNoteStored;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizStatsRedisService {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${quiz.stats.redis-prefix:finEdu:quiz:stats:}")
    private String redisPrefix;

    @Value("${quiz.stats.timezone:Asia/Seoul}")
    private String timezoneId;

    @Value("${quiz.stats.day-key-ttl-days:120}")
    private long dayKeyTtlDays;

    @Value("${quiz.stats.month-key-ttl-days:800}")
    private long monthKeyTtlDays;

    @Value("${quiz.stats.wrong-retention-days:60}")
    private int wrongRetentionDays;

    private ZoneId zone() {
        return ZoneId.of(timezoneId);
    }

    /**
     * 저번 주 같은 요일 00:00 ~ 오늘 23:59:59 (예: 화면의 "이번 주"와 오답 구간).
     * today.minusDays(7) 당일 시작 ~ 오늘 끝 (8일치 일별 합산과 동일 효과로 화~화 구간에 대응).
     */
    public LocalDate rollingWindowStartInclusive(LocalDate today) {
        return today.minusDays(7);
    }

    public void recordAttempt(QuizAttemptRequest req) {
        String userId = requireUserId(req.userId());
        LocalDate today = LocalDate.now(zone());
        long nowMilli = Instant.now().toEpochMilli();

        if (!req.correct()) {
            validateWrongPayload(req);
        }

        String dayKey = dayKey(userId, today);
        String monthKey = monthKey(userId, YearMonth.from(today));

        if (req.correct()) {
            stringRedisTemplate.opsForHash().increment(dayKey, "c", 1);
            stringRedisTemplate.opsForHash().increment(monthKey, "c", 1);
        } else {
            stringRedisTemplate.opsForHash().increment(dayKey, "w", 1);
            stringRedisTemplate.opsForHash().increment(monthKey, "w", 1);

            QuizWrongNoteStored stored = new QuizWrongNoteStored(
                    UUID.randomUUID().toString(),
                    req.question().trim(),
                    req.userAnswer().trim(),
                    req.correctAnswer().trim(),
                    req.explanation() == null || req.explanation().isBlank() ? null : req.explanation().trim(),
                    nowMilli
            );
            try {
                String json = objectMapper.writeValueAsString(stored);
                stringRedisTemplate.opsForZSet().add(wrongZsetKey(userId), json, nowMilli);
            } catch (JacksonException e) {
                throw new BusinessException(ErrorCode.INTERNAL_ERROR, "오답 노트 직렬화 실패", e);
            }
        }

        touchKeyTtl(dayKey, Duration.ofDays(dayKeyTtlDays));
        touchKeyTtl(monthKey, Duration.ofDays(monthKeyTtlDays));
        touchKeyTtl(wrongZsetKey(userId), Duration.ofDays(wrongRetentionDays + 30L));

        trimOldWrongNotes(userId, nowMilli);
    }

    public QuizStatsDashboardResponse getDashboard(String userId) {
        String uid = requireUserId(userId);
        LocalDate today = LocalDate.now(zone());
        LocalDate start = rollingWindowStartInclusive(today);

        int rollCorrect = 0;
        int rollWrong = 0;
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            rollCorrect += hashInt(dayKey(uid, d), "c");
            rollWrong += hashInt(dayKey(uid, d), "w");
        }
        QuizRateSummaryDto rolling = toRollSummary(rollCorrect, rollWrong);

        YearMonth ym = YearMonth.from(today);
        int mC = hashInt(monthKey(uid, ym), "c");
        int mW = hashInt(monthKey(uid, ym), "w");
        QuizRateSummaryDto month = toRollSummary(mC, mW);

        long startMilli = start.atStartOfDay(zone()).toInstant().toEpochMilli();
        long endMilli = today.atTime(23, 59, 59, 999_000_000).atZone(zone()).toInstant().toEpochMilli();

        List<QuizWrongNoteDto> wrongs = loadWrongNotes(uid, startMilli, endMilli);

        return new QuizStatsDashboardResponse(start, today, rolling, month, wrongs);
    }

    private List<QuizWrongNoteDto> loadWrongNotes(String userId, long minScore, long maxScore) {
        var range = stringRedisTemplate.opsForZSet().reverseRangeByScore(wrongZsetKey(userId), minScore, maxScore);
        if (range == null || range.isEmpty()) {
            return List.of();
        }
        List<QuizWrongNoteDto> out = new ArrayList<>();
        for (String json : range) {
            try {
                QuizWrongNoteStored s = objectMapper.readValue(json, QuizWrongNoteStored.class);
                out.add(new QuizWrongNoteDto(
                        s.id(),
                        s.question(),
                        s.userAnswer(),
                        s.correctAnswer(),
                        s.explanation(),
                        Instant.ofEpochMilli(s.answeredAtEpochMilli())
                ));
            } catch (JacksonException e) {
                log.warn("오답 노트 JSON 스킵: {}", e.getMessage());
            }
        }
        return out;
    }

    private void trimOldWrongNotes(String userId, long nowMilli) {
        long cutoff = nowMilli - Duration.ofDays(wrongRetentionDays).toMillis();
        stringRedisTemplate.opsForZSet().removeRangeByScore(wrongZsetKey(userId), 0, cutoff);
    }

    private static QuizRateSummaryDto toRollSummary(int correct, int wrong) {
        int total = correct + wrong;
        int pct = total == 0 ? 0 : (int) Math.round((100.0 * correct) / total);
        return new QuizRateSummaryDto(correct, wrong, total, pct);
    }

    private int hashInt(String key, String field) {
        Object v = stringRedisTemplate.opsForHash().get(key, field);
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void touchKeyTtl(String key, Duration ttl) {
        Boolean has = stringRedisTemplate.hasKey(key);
        if (Boolean.TRUE.equals(has)) {
            stringRedisTemplate.expire(key, ttl);
        }
    }

    private String dayKey(String userId, LocalDate d) {
        return redisPrefix + "day:" + userId + ":" + d;
    }

    private String monthKey(String userId, YearMonth ym) {
        return redisPrefix + "month:" + userId + ":" + ym;
    }

    private String wrongZsetKey(String userId) {
        return redisPrefix + "wrong:" + userId;
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }

    private static void validateWrongPayload(QuizAttemptRequest req) {
        if (req.question() == null || req.question().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오답 제출 시 question 은 필수입니다.");
        }
        if (req.userAnswer() == null || req.userAnswer().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오답 제출 시 userAnswer 는 필수입니다.");
        }
        if (req.correctAnswer() == null || req.correctAnswer().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "오답 제출 시 correctAnswer 는 필수입니다.");
        }
    }
}
