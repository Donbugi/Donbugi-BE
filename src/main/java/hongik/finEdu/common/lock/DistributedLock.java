package hongik.finEdu.common.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Redis 기반 분산 락 (SET NX PX + 토큰 검증 해제).
 *
 * <p>용도</p>
 * <ul>
 *   <li>스케줄러 중복 실행 방지 (ShedLock 역할) — 인스턴스가 여러 대여도 한 곳에서만 실행</li>
 *   <li>퀴즈 생성 같은 single-flight 구간</li>
 * </ul>
 *
 * <p>해제는 Lua 로 "내 토큰일 때만 DEL" — TTL 만료 후 다른 인스턴스가 잡은 락을 지우는 문제 방지.</p>
 */
@Slf4j
@Component
public class DistributedLock {

    private static final String PREFIX = "finEdu:lock:";

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public DistributedLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 락 획득 시 토큰 반환. 실패하면 empty. Redis 장애 시에도 empty(안전하게 실행 안 함). */
    public Optional<String> tryAcquire(String name, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean ok = redis.opsForValue().setIfAbsent(PREFIX + name, token, ttl);
            return Boolean.TRUE.equals(ok) ? Optional.of(token) : Optional.empty();
        } catch (Exception e) {
            log.warn("[lock] {} 획득 실패(Redis 오류): {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    public void release(String name, String token) {
        try {
            redis.execute(RELEASE, List.of(PREFIX + name), token);
        } catch (Exception e) {
            log.warn("[lock] {} 해제 실패(TTL 로 자동 만료): {}", name, e.getMessage());
        }
    }

    /**
     * 락을 잡은 경우에만 실행. 스케줄러용.
     * @return 실행했으면 true
     */
    public boolean runExclusively(String name, Duration ttl, Runnable task) {
        Optional<String> token = tryAcquire(name, ttl);
        if (token.isEmpty()) {
            log.debug("[lock] {} 다른 인스턴스가 실행 중 — skip", name);
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            release(name, token.get());
        }
    }

    public <T> Optional<T> callExclusively(String name, Duration ttl, Supplier<T> task) {
        Optional<String> token = tryAcquire(name, ttl);
        if (token.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(task.get());
        } finally {
            release(name, token.get());
        }
    }
}
