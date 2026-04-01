package hongik.finEdu.quiz.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.newscrawler.entity.Article;
import hongik.finEdu.newscrawler.repository.ArticleRepository;
import hongik.finEdu.quiz.dto.QuizResponseDto;
import hongik.finEdu.quiz.dto.RandomQuizPackItemDto;
import hongik.finEdu.quiz.entity.Quiz;
import hongik.finEdu.quiz.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private final StringRedisTemplate redisTemplate;
    private final QuizRepository quizRepository;
    private final ArticleRepository articleRepository;
    private final AiQuizClient aiQuizClient;
    private final ObjectMapper objectMapper;

    @Value("${quiz.cache-ttl-days:7}")
    private int cacheTtlDays;

    @Value("${quiz.lock-ttl-seconds:30}")
    private int lockTtlSeconds;

    private static final String CACHE_PREFIX = "quiz:";
    private static final String LOCK_PREFIX = "lock:quiz:";
    private static final int POLL_INTERVAL_MS = 500;
    private static final int POLL_MAX_WAIT_MS = 10_000;
    private static final int RANDOM_PACK_SIZE_MAX = 10;

    /**
     * DB에서 본문이 있는 기사를 무작위로 뽑아 기사별 퀴즈를 생성·조회한다.
     * 기존 {@link #getQuiz(Long)} (캐시·락·AI) 로직을 그대로 재사용한다.
     *
     * @param size 1~10, 보통 3
     */
    public List<RandomQuizPackItemDto> getRandomQuizPack(int size) {
        if (size < 1 || size > RANDOM_PACK_SIZE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size는 1~" + RANDOM_PACK_SIZE_MAX + " 사이여야 합니다 (got " + size + ")");
        }

        List<Article> articles = articleRepository.findRandomArticlesWithContent(size);
        if (articles.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ARTICLES_FOR_QUIZ);
        }

        return articles.stream()
                .map(article -> RandomQuizPackItemDto.builder()
                        .articleId(article.getArticleId())
                        .title(article.getTitle())
                        .category(article.getCategory())
                        .quizzes(getQuiz(article.getArticleId()))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 기사 ID로 퀴즈 조회
     * Redis 캐시 -> DB -> AI 서버 순으로 탐색
     */
    public List<QuizResponseDto> getQuiz(Long articleId) {
        String cacheKey = CACHE_PREFIX + articleId;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.info("[캐시 HIT] articleId={}", articleId);
            return deserialize(cached);
        }

        String lockKey = LOCK_PREFIX + articleId;
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(lockTtlSeconds));

        if (Boolean.TRUE.equals(lockAcquired)) {
            try {
                return generateAndCache(articleId, cacheKey);
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        return pollForCache(articleId, cacheKey);
    }

    private List<QuizResponseDto> generateAndCache(Long articleId, String cacheKey) {
        var dbQuiz = quizRepository.findByArticleId(articleId);
        if (dbQuiz.isPresent()) {
            String json = dbQuiz.get().getQuizJson();
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofDays(cacheTtlDays));
            log.info("[DB HIT → 캐시 복구] articleId={}", articleId);
            return deserialize(json);
        }

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND,
                        "articleId=" + articleId));

        String quizJson = aiQuizClient.generateQuiz(articleId, article.getContent());

        redisTemplate.opsForValue().set(cacheKey, quizJson, Duration.ofDays(cacheTtlDays));
        log.info("[캐시 저장] articleId={}", articleId);

        saveToDbAsync(articleId, quizJson);

        return deserialize(quizJson);
    }

    private List<QuizResponseDto> pollForCache(Long articleId, String cacheKey) {
        log.info("[폴링 대기] articleId={}", articleId);
        long waited = 0;

        while (waited < POLL_MAX_WAIT_MS) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.QUIZ_GENERATION_TIMEOUT,
                        "인터럽트 발생, articleId=" + articleId, e);
            }
            waited += POLL_INTERVAL_MS;

            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("[폴링 성공] articleId={}, 대기 {}ms", articleId, waited);
                return deserialize(cached);
            }
        }

        throw new BusinessException(ErrorCode.QUIZ_GENERATION_TIMEOUT,
                "10초 초과, articleId=" + articleId);
    }

    @Async
    public void saveToDbAsync(Long articleId, String quizJson) {
        try {
            Quiz quiz = Quiz.builder()
                    .articleId(articleId)
                    .quizJson(quizJson)
                    .build();
            quizRepository.save(quiz);
            log.info("[Write-Behind] DB 저장 완료: articleId={}", articleId);
        } catch (DataIntegrityViolationException e) {
            log.info("[Write-Behind] 이미 저장됨, 무시: articleId={}", articleId);
        } catch (Exception e) {
            log.error("[Write-Behind] DB 저장 실패: articleId={}, error={}", articleId, e.getMessage());
        }
    }

    private List<QuizResponseDto> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, "캐시 JSON 파싱 실패", e);
        }
    }
}
