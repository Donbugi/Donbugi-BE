package hongik.finEdu.points.service;

import hongik.finEdu.common.lock.DistributedLock;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.repository.PointHistoryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 포인트 정합성 검증: point_account.balance == Σ point_history.delta.
 * 불일치 시 ERROR 로그 + points.reconciliation.mismatch 메트릭 (자동 보정은 하지 않음 — 원인 분석 우선).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointReconciliationJob {

    private final PointAccountRepository accountRepository;
    private final PointHistoryRepository historyRepository;
    private final DistributedLock distributedLock;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void run() {
        distributedLock.runExclusively("sched:point-reconcile", Duration.ofMinutes(30), this::reconcileAll);
    }

    public int reconcileAll() {
        int mismatches = 0;
        for (String userId : accountRepository.findAllUserIds()) {
            int balance = accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
            long sum = historyRepository.sumDelta(userId);
            if (sum != balance) {
                mismatches++;
                log.error("[포인트 정합성] userId={} balance={} Σhistory={}", userId, balance, sum);
                meterRegistry.counter("points.reconciliation.mismatch").increment();
            }
        }
        log.info("[포인트 정합성] 불일치 {}건", mismatches);
        return mismatches;
    }
}
