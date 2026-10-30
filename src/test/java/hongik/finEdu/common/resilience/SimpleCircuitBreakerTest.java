package hongik.finEdu.common.resilience;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleCircuitBreakerTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-29T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 연속_실패_임계치에서_열리고_대기_후_한_건만_시험_통과() {
        MutableClock clock = new MutableClock();
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker("t", 3, Duration.ofSeconds(30), clock);
        cb.onFailure();
        cb.onFailure();
        assertThat(cb.allowRequest()).isTrue();
        cb.onFailure();
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.OPEN);
        assertThat(cb.allowRequest()).isFalse();

        clock.now = clock.now.plusSeconds(31);
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.HALF_OPEN);
        assertThat(cb.allowRequest()).isTrue();   // 시험 요청
        assertThat(cb.allowRequest()).isFalse();  // 동시에 들어온 나머지는 차단

        cb.onSuccess();
        assertThat(cb.state()).isEqualTo(SimpleCircuitBreaker.State.CLOSED);
        assertThat(cb.allowRequest()).isTrue();
    }
}
