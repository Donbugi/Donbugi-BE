package hongik.finEdu.news.interest.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.news.interest.dto.NewsInterestReadRequest;
import hongik.finEdu.news.interest.dto.NewsInterestTopicDto;
import hongik.finEdu.news.interest.dto.NewsInterestTrendsResponse;
import hongik.finEdu.newscrawler.entity.Article;
import hongik.finEdu.newscrawler.repository.ArticleRepository;
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
    private final ArticleRepository articleRepository;

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

    /**
     * 뉴스 읽음 기록.
     * <ul>
     *   <li>articleId 가 있으면: 기사 카테고리는 서버가 조회, (사용자·날짜·기사) 중복 제거(SADD),
     *       오늘 <b>서로 다른</b> 기사 5·10·15…개마다 +20P (하루 최대 {@link PointPolicy#NEWS_READ_DAILY_MAX_MILESTONES}회)</li>
     *   <li>articleId 가 없으면(구버전 클라이언트): 관심 카테고리 집계만, 보상 없음</li>
     * </ul>
     * 기존에는 같은 기사로 5번 호출할 때마다 20P 가 무제한 지급됐다.
     */
    public void recordRead(NewsInterestReadRequest req) {
        String userId = requireUserId(req.userId());
        LocalDate today = LocalDate.now(zone());

        String cat;
        Long articleId = req.articleId();
        if (articleId != null) {
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "articleId=" + articleId));
            cat = article.getCategory() != null && !article.getCategory().isBlank()
                    ? article.getCategory().trim()
                    : normalizeCategory(req.category());
            String dailySetKey = redisPrefix + "dailyread:set:" + userId + ":" + today;
            Long added = stringRedisTemplate.opsForSet().add(dailySetKey, String.valueOf(articleId));
            stringRedisTemplate.expire(dailySetKey, Duration.ofDays(3));
            if (added == null || added == 0) {
                return; // 오늘 이미 읽은 기사 → 관심도·보상 모두 반영하지 않음
            }
            incrementInterest(userId, cat);
            Long distinctToday = stringRedisTemplate.opsForSet().size(dailySetKey);
            awardMilestoneIfReached(userId, today, distinctToday);
        } else {
            cat = normalizeCategory(req.category());
            incrementInterest(userId, cat);
        }
    }

    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "category 또는 articleId는 필수입니다.");
        }
        String cat = raw.trim();
        return cat.length() > CATEGORY_MAX_LEN ? cat.substring(0, CATEGORY_MAX_LEN) : cat;
    }

    private void incrementInterest(String userId, String cat) {
        String key = monthCountKey(userId, YearMonth.now(zone()));
        stringRedisTemplate.opsForHash().increment(key, cat, 1);
        stringRedisTemplate.expire(key, Duration.ofDays(monthKeyTtlDays));
    }

    private void awardMilestoneIfReached(String userId, LocalDate today, Long distinctToday) {
        if (distinctToday == null || distinctToday <= 0
                || distinctToday % PointPolicy.NEWS_READ_MILESTONE_INTERVAL != 0) {
            return;
        }
        long milestone = distinctToday / PointPolicy.NEWS_READ_MILESTONE_INTERVAL;
        if (milestone > PointPolicy.NEWS_READ_DAILY_MAX_MILESTONES) {
            return;
        }
        pointRewardService.tryAwardOnce(
                userId,
                "newsread:daily:" + userId + ":" + today + ":m:" + milestone,
                PointPolicy.NEWS_READ_MILESTONE_POINTS,
                "오늘 뉴스 " + distinctToday + "개 읽기 달성",
                PointPolicy.NEWS_READ_MILESTONE_INTERVAL + "개마다 " + PointPolicy.NEWS_READ_MILESTONE_POINTS + "P");
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
