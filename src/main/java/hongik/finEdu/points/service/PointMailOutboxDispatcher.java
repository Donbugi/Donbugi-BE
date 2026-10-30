package hongik.finEdu.points.service;

import hongik.finEdu.common.lock.DistributedLock;
import hongik.finEdu.points.dto.RedeemLedgerResult;
import hongik.finEdu.points.entity.PointMailOutbox;
import hongik.finEdu.points.repository.PointMailOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 포인트 교환 안내 메일 outbox 발송기.
 * <ul>
 *   <li>커밋 직후 비동기로 1회 즉시 시도 (dispatchSoon)</li>
 *   <li>30초마다 재시도 대상 일괄 처리 (지수 백오프, 최대 5회)</li>
 *   <li>행 잠금(FOR UPDATE) + 상태 확인으로 중복 발송 방지</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointMailOutboxDispatcher {

    private final PointMailOutboxRepository outboxRepository;
    private final PointRewardEmailService emailService;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLock distributedLock;

    @Async
    public void dispatchSoon(String redemptionRef) {
        outboxRepository.findByRedemptionRef(redemptionRef).ifPresent(o -> dispatch(o.getId()));
    }

    @Scheduled(fixedDelayString = "${app.points.mail-outbox-poll-ms:30000}", initialDelay = 30_000)
    public void dispatchDue() {
        distributedLock.runExclusively("sched:point-mail-outbox", Duration.ofMinutes(2), () -> {
            for (Long id : outboxRepository.findDueIds(LocalDateTime.now(), PageRequest.of(0, 50))) {
                dispatch(id);
            }
        });
    }

    void dispatch(Long outboxId) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                PointMailOutbox o = outboxRepository.findByIdForUpdate(outboxId).orElse(null);
                if (o == null || o.getStatus() != PointMailOutbox.Status.PENDING) {
                    return;
                }
                if (!emailService.isConfigured()) {
                    emailService.sendRedemptionNotice(toResult(o)); // 본문을 로그로 남김
                    o.markSkipped();
                    return;
                }
                if (emailService.sendRedemptionNotice(toResult(o))) {
                    o.markSent();
                } else {
                    o.markFailed("SMTP 발송 실패");
                    log.warn("[mail-outbox] 발송 실패 ref={} attempts={}", o.getRedemptionRef(), o.getAttempts());
                }
            });
        } catch (Exception e) {
            log.warn("[mail-outbox] 처리 오류 id={}: {}", outboxId, e.getMessage());
        }
    }

    private static RedeemLedgerResult toResult(PointMailOutbox o) {
        return new RedeemLedgerResult(o.getRedemptionRef(), o.getUserId(), o.getEmail(), o.getBenefitCode(),
                o.getPointsSpent(), o.getBalanceAfter(), false);
    }
}
