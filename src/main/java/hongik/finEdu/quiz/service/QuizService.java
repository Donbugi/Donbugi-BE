package hongik.finEdu.quiz.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.common.lock.DistributedLock;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    private final DistributedLock distributedLock;
    private final QuizPersistService quizPersistService;

    /** 같은 인스턴스 내 진행 중 생성 공유 */
    private final ConcurrentHashMap<Long, CompletableFuture<List<QuizResponseDto>>> inflight = new ConcurrentHashMap<>();

    @Value("${quiz.cache-ttl-days:7}")
    private int cacheTtlDays;

    @Value("${quiz.lock-ttl-seconds:300}")
    private int lockTtlSeconds;

    private static final String CACHE_PREFIX = "quiz:";
    private static final String LOCK_NAME_PREFIX = "quiz-gen:";
    /** AI read timeout(60초) 이상 대기 */
    private static final long POLL_MAX_WAIT_MS = 90_000;
    private static final int RANDOM_PACK_SIZE_MAX = 10;

    /** random-session: DB에서 한 번에 가져올 저장 퀴즈 행 수 (중복 문항 제거 여유) */
    private static final int SESSION_DB_FETCH_MULTIPLIER = 4;

    private record QuizCandidate(Long articleId, String articleTitle, QuizResponseDto quiz) {
    }

    /**
     * 홈 '오늘의 과제'용. DB에 이미 저장된 퀴즈만 사용 (실시간 AI 호출 없음 → 빠른 응답).
     * 기사 상세 등은 {@link #getQuiz(Long)} 로 캐시·DB·AI 경로를 그대로 사용.
     */
    public List<QuizSessionItemDto> getRandomQuizSession(int size) {
        if (size < 1 || size > RANDOM_PACK_SIZE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "size는 1~" + RANDOM_PACK_SIZE_MAX + " 사이여야 합니다 (got " + size + ")");
        }

        int fetchLimit = Math.min(50, Math.max(size, size * SESSION_DB_FETCH_MULTIPLIER));
        List<Quiz> stored = sampleStored(fetchLimit);
        if (stored.isEmpty()) {
            throw new BusinessException(ErrorCode.NO_ARTICLES_FOR_QUIZ,
                    "저장된 퀴즈가 없습니다. 기사 상세에서 퀴즈를 생성한 뒤 다시 시도해 주세요.");
        }

        var articleIds = stored.stream().map(Quiz::getArticleId).distinct().toList();
        var titleByArticleId = articleRepository.findAllById(articleIds).stream()
                .collect(Collectors.toMap(Article::getArticleId, Article::getTitle));

        List<QuizCandidate> pool = new ArrayList<>();
        for (Quiz row : stored) {
            String title = titleByArticleId.getOrDefault(row.getArticleId(), "");
            try {
                for (QuizResponseDto q : deserialize(row.getQuizJson())) {
                    if (isUsableQuiz(q)) {
                        pool.add(new QuizCandidate(row.getArticleId(), title, q));
                    }
                }
            } catch (BusinessException ex) {
                log.debug("[random-session] articleId={} 스킵: {}", row.getArticleId(), ex.getMessage());
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

    /** 무작위 시작점 샘플링 (ORDER BY random() 제거) */
    private List<Quiz> sampleStored(int limit) {
        Long min = quizRepository.findMinId();
        Long max = quizRepository.findMaxId();
        if (min == null || max == null) {
            return List.of();
        }
        long start = min + ThreadLocalRandom.current().nextLong(max - min + 1);
        return quizRepository.findFromRandomStart(start, limit);
    }

    /**
     * 기사 ID로 퀴즈 조회: Redis 캐시 → DB → AI 생성.
     *
     * <p>동시 요청 처리 (single-flight)</p>
     * <ul>
     *   <li>같은 인스턴스: 진행 중인 생성 Future 를 공유 (스레드가 sleep 하지 않음)</li>
     *   <li>다른 인스턴스: Redis 분산 락(토큰 검증 해제, TTL 300초 > AI 최대 소요시간(60초×4회+백오프)).
     *       기존에는 락 TTL(30초)이 AI 타임아웃(60초×재시도)보다 짧아 중복 생성되고,
     *       무조건 DEL 로 남의 락을 지울 수 있었으며, 대기 측은 10초 만에 타임아웃됐다.</li>
     * </ul>
     */
    public List<QuizResponseDto> getQuiz(Long articleId) {
        String cacheKey = CACHE_PREFIX + articleId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return deserialize(cached);
        }

        CompletableFuture<List<QuizResponseDto>> mine = new CompletableFuture<>();
        CompletableFuture<List<QuizResponseDto>> running = inflight.putIfAbsent(articleId, mine);
        if (running != null) {
            return await(running, articleId);
        }
        try {
            List<QuizResponseDto> result = loadOrGenerate(articleId, cacheKey);
            mine.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(articleId, mine);
        }
    }

    /** 이미 생성된 퀴즈가 있는지 (사전 생성 스킵 판단용) */
    public boolean hasQuiz(Long articleId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(CACHE_PREFIX + articleId))
                || quizRepository.existsByArticleId(articleId);
    }

    private List<QuizResponseDto> loadOrGenerate(Long articleId, String cacheKey) {
        var dbQuiz = quizRepository.findByArticleId(articleId);
        if (dbQuiz.isPresent()) {
            String json = dbQuiz.get().getQuizJson();
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofDays(cacheTtlDays));
            return deserialize(json);
        }

        Optional<String> token = distributedLock.tryAcquire(LOCK_NAME_PREFIX + articleId, Duration.ofSeconds(lockTtlSeconds));
        if (token.isEmpty()) {
            return pollForCache(articleId, cacheKey); // 다른 인스턴스가 생성 중
        }
        try {
            String again = redisTemplate.opsForValue().get(cacheKey);
            if (again != null) {
                return deserialize(again);
            }
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND, "articleId=" + articleId));
            if (article.getContent() == null || article.getContent().isBlank()) {
                throw new BusinessException(ErrorCode.NO_ARTICLES_FOR_QUIZ, "본문 없는 기사 articleId=" + articleId);
            }
            String quizJson = aiQuizClient.generateQuiz(articleId, article.getContent());
            redisTemplate.opsForValue().set(cacheKey, quizJson, Duration.ofDays(cacheTtlDays));
            quizPersistService.saveAsync(articleId, quizJson); // 별도 빈 → @Async 실제 적용
            return deserialize(quizJson);
        } finally {
            distributedLock.release(LOCK_NAME_PREFIX + articleId, token.get());
        }
    }

    private List<QuizResponseDto> await(CompletableFuture<List<QuizResponseDto>> f, Long articleId) {
        try {
            return f.get(POLL_MAX_WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.QUIZ_GENERATION_TIMEOUT, "인터럽트, articleId=" + articleId, e);
        } catch (TimeoutException e) {
            throw new BusinessException(ErrorCode.QUIZ_GENERATION_TIMEOUT, "articleId=" + articleId, e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR, e.getMessage(), e);
        }
    }

    /** 다른 인스턴스가 생성 중일 때: 지수 백오프로 캐시 확인 (최대 POLL_MAX_WAIT_MS) */
    private List<QuizResponseDto> pollForCache(Long articleId, String cacheKey) {
        long waited = 0;
        long interval = 250;
        while (waited < POLL_MAX_WAIT_MS) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.QUIZ_GENERATION_TIMEOUT, "인터럽트, articleId=" + articleId, e);
            }
            waited += interval;
            interval = Math.min(interval * 2, 2000);
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return deserialize(cached);
            }
        }
        throw new BusinessException(ErrorCode.QUIZ_GENERATION_TIMEOUT,
                (POLL_MAX_WAIT_MS / 1000) + "초 초과, articleId=" + articleId);
    }

    private List<QuizResponseDto> deserialize(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JacksonException e) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_PARSE_ERROR, "캐시 JSON 파싱 실패", e);
        }
    }
}
