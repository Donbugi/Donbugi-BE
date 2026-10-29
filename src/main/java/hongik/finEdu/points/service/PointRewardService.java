package hongik.finEdu.points.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.entity.PointHistoryEntry;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.repository.PointHistoryRepository;
import hongik.finEdu.points.repository.PointRewardClaimRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 정책 기반 포인트 지급의 단일 진입점.
 * <ul>
 *   <li>claimKey 로 멱등 (같은 키는 한 번만 지급)</li>
 *   <li>계정 행 잠금으로 잔액 갱신 직렬화</li>
 *   <li>사용자별 일일 획득 상한 (app.points.daily-earn-cap)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointRewardService {

    private final PointRewardClaimRepository claimRepository;
    private final PointAccountRepository accountRepository;
    private final PointHistoryRepository historyRepository;
    private final PointAccountLocker accountLocker;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PointActivityCacheService pointActivityCacheService;

    @Value("${app.points.timezone:Asia/Seoul}")
    private String timezoneId;

    /** 하루 최대 획득 포인트 (0 이하이면 무제한) */
    @Value("${app.points.daily-earn-cap:500}")
    private int dailyEarnCap;

    @Transactional
    public PointAwardResult tryAwardOnce(String userId, String claimKey, int points, String title, String detail) {
        if (claimKey == null || claimKey.isBlank() || claimKey.length() > 210) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "claimKey가 유효하지 않습니다.");
        }
        if (points <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "포인트는 양수여야 합니다.");
        }

        // 계정 잠금을 먼저 잡아 같은 사용자의 지급 요청을 직렬화 → 일일 상한 계산이 race 없이 정확해진다.
        PointAccount acc = accountLocker.lockOrCreate(userId, 0).account();

        if (claimRepository.existsByClaimKey(claimKey)) {
            return new PointAwardResult(false, 0, acc.getBalance());
        }

        int grant = applyDailyCap(userId, points);
        if (grant <= 0) {
            log.info("[points] 일일 상한 도달 userId={} claim={}", userId, claimKey);
            return new PointAwardResult(false, 0, acc.getBalance());
        }

        int inserted = claimRepository.insertIfAbsent(userId, claimKey, grant, title, LocalDateTime.now());
        if (inserted == 0) {
            return new PointAwardResult(false, 0, acc.getBalance());
        }

        acc.credit(grant);
        accountRepository.save(acc);
        historyRepository.save(PointHistoryEntry.builder()
                .userId(userId)
                .delta(grant)
                .title(title)
                .detail(detail)
                .relatedRef(claimKey)
                .build());
        afterCommitExecutor.runAfterCommit("point-activity-redis",
                () -> pointActivityCacheService.refreshFromDb(userId));
        return new PointAwardResult(true, grant, acc.getBalance());
    }

    private int applyDailyCap(String userId, int points) {
        if (dailyEarnCap <= 0) {
            return points;
        }
        ZoneId zone = ZoneId.of(timezoneId);
        LocalDate today = LocalDate.now(zone);
        // occurred_at 은 JVM 기본 타임존 기준 LocalDateTime 으로 저장되므로 서울 하루 경계를 그 기준으로 변환
        LocalDateTime from = today.atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime to = today.plusDays(1).atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        int earnedToday = historyRepository.sumPositiveDeltaBetween(userId, from, to);
        return Math.min(points, dailyEarnCap - earnedToday);
    }
}
