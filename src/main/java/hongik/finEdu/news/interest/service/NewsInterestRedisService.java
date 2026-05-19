package hongik.finEdu.news.interest.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.news.interest.dto.NewsInterestReadRequest;
import hongik.finEdu.news.interest.dto.NewsInterestTopicDto;
import hongik.finEdu.news.interest.dto.NewsInterestTrendsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NewsInterestRedisService {

    private static final int CATEGORY_MAX_LEN = 80;
    private static final int TOPIC_DEFAULT_LIMIT = 5;

    private final StringRedisTemplate stringRedisTemplate;
    private final NewsInterestAiClient newsInterestAiClient;

    @Value("${news.interest.redis-prefix:finEdu:news:interest:}")
    private String redisPrefix;

    @Value("${news.interest.timezone:Asia/Seoul}")
    private String timezoneId;

    @Value("${news.interest.month-key-ttl-days:400}")
    private long monthKeyTtlDays;

    @Value("${news.interest.insight-cache-ttl-minutes:30}")
    private long insightCacheTtlMinutes;

    private ZoneId zone() {
        return ZoneId.of(timezoneId);
    }

    /** 이번 달(서울) 해당 카테고리(또는 토픽) 읽은 횟수 +1 */
    public void recordRead(NewsInterestReadRequest req) {
        String userId = requireUserId(req.userId());
        if (req.category() == null || req.category().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "category는 필수입니다.");
        }
        String cat = req.category().trim();
        if (cat.length() > CATEGORY_MAX_LEN) {
            cat = cat.substring(0, CATEGORY_MAX_LEN);
        }

        YearMonth ym = YearMonth.now(zone());
        String key = monthCountKey(userId, ym);

        stringRedisTemplate.opsForHash().increment(key, cat, 1);
        stringRedisTemplate.expire(key, Duration.ofDays(monthKeyTtlDays));
    }

    public NewsInterestTrendsResponse getTrends(String userId, Integer top) {
        String uid = requireUserId(userId);
        int limit = top == null || top < 1 ? TOPIC_DEFAULT_LIMIT : Math.min(top, 20);

        YearMonth ym = YearMonth.now(zone());
        String key = monthCountKey(uid, ym);

        Map<Object, Object> raw = stringRedisTemplate.opsForHash().entries(key);
        List<NewsInterestTopicDto> sorted = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                String name = e.getKey().toString();
                int count = parseCount(e.getValue());
                if (count > 0) {
                    sorted.add(new NewsInterestTopicDto(name, count));
                }
            }
            sorted.sort(Comparator.<NewsInterestTopicDto>comparingInt(NewsInterestTopicDto::count)
                    .reversed()
                    .thenComparing(NewsInterestTopicDto::name));
        }

        List<NewsInterestTopicDto> topList = sorted.stream().limit(limit).toList();

        String ai = resolveAiInsight(uid, ym, topList);
        return new NewsInterestTrendsResponse(ym, topList, ai);
    }

    private String resolveAiInsight(String userId, YearMonth ym, List<NewsInterestTopicDto> topList) {
        if (topList.isEmpty()) {
            return "아직 이번 달에 읽은 뉴스 기록이 없어요. 관심 카테고리 뉴스를 살펴 보세요.";
        }

        String cacheKey = insightCacheKey(userId, ym, topList);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }

        String fallback = "관심 토픽을 바탕으로 한쪽으로 치우치지 않게 다양한 금융·경제 뉴스도 함께 살펴 보시면 도움이 됩니다.";

        String text = newsInterestAiClient.requestInsight(topList).orElse(fallback);
        stringRedisTemplate.opsForValue().set(cacheKey, text, Duration.ofMinutes(insightCacheTtlMinutes));
        return text;
    }

    private String insightCacheKey(String userId, YearMonth ym, List<NewsInterestTopicDto> topList) {
        String sigPayload = String.join("|",
                topList.stream().map(t -> t.name() + ":" + t.count()).toList());
        String hash = sha256Short(sigPayload);
        return redisPrefix + "insight:" + userId + ":" + ym + ":" + hash;
    }

    private static String sha256Short(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, 8);
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }

    private String monthCountKey(String userId, YearMonth ym) {
        return redisPrefix + "month:" + userId + ":" + ym;
    }

    private static int parseCount(Object v) {
        if (v == null) {
            return 0;
        }
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }
}
