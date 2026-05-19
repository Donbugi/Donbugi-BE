package hongik.finEdu.quiz.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.newscrawler.entity.Article;
import hongik.finEdu.newscrawler.repository.ArticleRepository;
import hongik.finEdu.quiz.dto.QuizResponseDto;
import hongik.finEdu.quiz.dto.QuizSessionItemDto;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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

    /** 랜덤 세션: 기사 묶음당 한 번에 가져올 개수 */
    private static final int SESSION_ARTICLE_BATCH = 8;
    /** 랜덤 세션: 퀴즈 풀 채우기 최대 라운드 (기사 부족·AI 실패 대비) */
    private static final int SESSION_MAX_ROUNDS = 10;

    private record QuizCandidate(Long articleId, String articleTitle, QuizResponseDto quiz) {
    }

    /**
     * 여러 기사에서 AI(또는 DB/캐시) 퀴즈를 모은 뒤, 중복 문항을 빼고 무작위로 {@code size}개만 반환.
     * 화면: Q1~Qn + 객관식 보기 나열.
     */
    public List<QuizSessionItemDto> getRandomQuizSession(int size) {
        if (size < 1 || size > RANDOM_PACK_SIZE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size는 1~" + RANDOM_PACK_SIZE_MAX + " 사이여야 합니다 (got " + size + ")");
        }

        List<QuizCandidate> pool = new ArrayList<>();
        for (int round = 0; round < SESSION_MAX_ROUNDS && pool.size() < size * 6; round++) {
            List<Article> articles = articleRepository.findRandomArticlesWithContent(SESSION_ARTICLE_BATCH);
            if (articles.isEmpty()) {
                break;
            }
            for (Article a : articles) {
                try {
                    List<QuizResponseDto> qs = getQuiz(a.getArticleId());
                    for (QuizResponseDto q : qs) {
                        if (isUsableQuiz(q)) {
                            pool.add(new QuizCandidate(a.getArticleId(), a.getTitle(), q));
                        }
                    }
                } catch (BusinessException ex) {
                    log.debug("[random-session] articleId={} 스킵: {}", a.getArticleId(), ex.getMessage());
                }
            }
        }

        Collections.shuffle(pool, ThreadLocalRandom.current());

        List<QuizSessionItemDto> picked = new ArrayList<>(size);
        Set<String> seenQuestions = new HashSet<>();
        for (QuizCandidate c : pool) {
            String norm = normalizeQuestionKey(c.quiz().getQuestion());
            if (!seenQuestions.add(norm)) {
                continue;
            }
            picked.add(toSessionItem(picked.size() + 1, c));
            if (picked.size() == size) {
                break;
            }
        }

        if (picked.size() < size) {
            throw new BusinessException(ErrorCode.NO_ARTICLES_FOR_QUIZ,
                    "무작위로 모은 퀴즈가 " + picked.size() + "개뿐입니다 (필요 " + size + "개). 기사·퀴즈를 더 쌓은 뒤 다시 시도해 주세요.");
        }
        return picked;
    }

    private static boolean isUsableQuiz(QuizResponseDto q) {
        if (q.getQuestion() == null || q.getQuestion().isBlank()) {
            return false;
        }
        if (q.getOptions() == null || q.getOptions().isEmpty()) {
            return false;
        }
        int idx = q.getCorrectIndex();
        return idx >= 0 && idx < q.getOptions().size();
    }

    private static String normalizeQuestionKey(String question) {
        return question.trim().replaceAll("\\s+", " ");
    }

    private static QuizSessionItemDto toSessionItem(int order, QuizCandidate c) {
        QuizResponseDto q = c.quiz();
        String title = c.articleTitle() == null ? "" : c.articleTitle();
        if (title.length() > 120) {
            title = title.substring(0, 117) + "...";
        }
        return new QuizSessionItemDto(
                order,
                c.articleId(),
                title,
                q.getQuestion().trim(),
                List.copyOf(q.getOptions()),
                q.getCorrectIndex(),
                q.getExplanation()
        );
    }

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
