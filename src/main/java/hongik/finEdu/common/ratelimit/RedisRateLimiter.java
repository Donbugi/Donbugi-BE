package hongik.finEdu.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Redis 고정 윈도우 카운터 rate limiter.
 * INCR + 첫 요청 시 PEXPIRE 를 Lua 로 원자 실행 → 다중 인스턴스에서도 동일 한도.
 * Redis 장애 시에는 요청을 막지 않는다(fail-open).
 */
@Slf4j
@Component
public class RedisRateLimiter {

    private static final DefaultRedisScript<Long> INCR_WITH_TTL = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return c
            """, Long.class);

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return 허용이면 true */
    public boolean tryAcquire(String bucket, String subject, int limit, Duration window) {
        long windowMs = window.toMillis();
        long slot = System.currentTimeMillis() / windowMs;
        String key = "finEdu:rl:" + bucket + ":" + subject + ":" + slot;
        try {
            Long count = redis.execute(INCR_WITH_TTL, List.of(key), String.valueOf(windowMs));
            return count == null || count <= limit;
        } catch (Exception e) {
            log.warn("[rate-limit] Redis 오류로 허용 처리(fail-open): {}", e.getMessage());
            return true;
        }
    }
}
