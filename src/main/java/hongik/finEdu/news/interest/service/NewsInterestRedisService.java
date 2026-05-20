package hongik.finEdu.news.interest.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.news.interest.dto.NewsInterestReadRequest;
import hongik.finEdu.news.interest.dto.NewsInterestTopicDto;
import hongik.finEdu.news.interest.dto.NewsInterestTrendsResponse;
import hongik.finEdu.points.policy.PointPolicy;
import hongik.finEdu.points.service.PointRewardService;
import hongik.finEdu.user.entity.AppUser;
import hongik.finEdu.user.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
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
    private final AppUserRepository appUserRepository;
    private final PointRewardService pointRewardService;

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

        LocalDate today = LocalDate.now(zone());
        String dailyReadKey = redisPrefix + "dailyread:" + userId + ":" + today;
        Long dailyTotal = stringRedisTemplate.opsForValue().increment(dailyReadKey);
        stringRedisTemplate.expire(dailyReadKey, Duration.ofDays(3));
        if (dailyTotal != null
                && dailyTotal > 0
                && dailyTotal % PointPolicy.NEWS_READ_MILESTONE_INTERVAL == 0) {
            pointRewardService.tryAwardOnce(
                    userId,
                    "newsread:daily:" + userId + ":" + today + ":hit:" + dailyTotal,
                    PointPolicy.NEWS_READ_MILESTONE_POINTS,
                    "오늘 뉴스 읽기 " + dailyTotal + "회 달성",
                    PointPolicy.NEWS_READ_MILESTONE_INTERVAL + "회마다 " + PointPolicy.NEWS_READ_MILESTONE_POINTS + "P");
        }
    }

    @Transactional
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

        String sig = topicSignature(topList);

        AppUser existing = appUserRepository.findByExternalUserId(userId).orElse(null);
        if (existing != null
                && ym.toString().equals(existing.getNewsInsightYearMonth())
                && sig.equals(existing.getNewsInsightSignature())
                && existing.getNewsInsightText() != null
                && !existing.getNewsInsightText().isBlank()) {
            return existing.getNewsInsightText();
        }

        String cacheKey = insightCacheKey(userId, ym, sig);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            persistInsight(userId, ym, sig, cached);
            return cached;
        }

        String fallback = "관심 토픽을 바탕으로 한쪽으로 치우치지 않게 다양한 금융·경제 뉴스도 함께 살펴 보시면 도움이 됩니다.";
        String text = newsInterestAiClient.requestInsight(topList).orElse(fallback);

        persistInsight(userId, ym, sig, text);
        stringRedisTemplate.opsForValue().set(cacheKey, text, Duration.ofMinutes(insightCacheTtlMinutes));
        return text;
    }

    private void persistInsight(String externalUserId, YearMonth ym, String signatureHex, String text) {
        appUserRepository.findByExternalUserId(externalUserId).ifPresent(u -> {
            u.setNewsInsightText(text);
            u.setNewsInsightYearMonth(ym.toString());
            u.setNewsInsightSignature(signatureHex);
            appUserRepository.save(u);
        });
    }

    private String insightCacheKey(String userId, YearMonth ym, String signatureHex) {
        return redisPrefix + "insight:" + userId + ":" + ym + ":" + signatureHex;
    }

    private static String topicSignature(List<NewsInterestTopicDto> topList) {
        String payload = String.join("|",
                topList.stream().map(t -> t.name() + ":" + t.count()).toList());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
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
