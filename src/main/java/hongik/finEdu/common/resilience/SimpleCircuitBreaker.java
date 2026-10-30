package hongik.finEdu.common.resilience;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 최소 서킷브레이커 (CLOSED → 연속 실패 N회 → OPEN(차단) → 대기 후 HALF_OPEN(1회 시험) → 성공 시 CLOSED).
 * 외부 의존(AI 서버) 장애 시 요청마다 타임아웃까지 기다리며 스레드를 묶어두는 것을 막는다.
 */
public class SimpleCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final Clock clock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<Instant> openedAt = new AtomicReference<>();

    public SimpleCircuitBreaker(String name, int failureThreshold, Duration openDuration, Clock clock) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
        this.clock = clock;
    }

    public State state() {
        Instant opened = openedAt.get();
        if (opened == null) {
            return State.CLOSED;
        }
        return clock.instant().isBefore(opened.plus(openDuration)) ? State.OPEN : State.HALF_OPEN;
    }

    /** 호출 허용 여부. HALF_OPEN 에서는 한 요청만 시험 통과시킨다. */
    public boolean allowRequest() {
        State s = state();
        if (s == State.CLOSED) {
            return true;
        }
        if (s == State.HALF_OPEN) {
            Instant opened = openedAt.get();
            // 시험 요청 1건만 통과: openedAt 을 지금으로 갱신해 나머지는 다시 OPEN 으로 본다
            return opened != null && openedAt.compareAndSet(opened, clock.instant());
        }
        return false;
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        openedAt.set(null);
    }

    public void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAt.set(clock.instant());
        }
    }

    public String name() {
        return name;
    }
}
