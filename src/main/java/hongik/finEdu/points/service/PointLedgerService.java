package hongik.finEdu.points.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.domain.PointBenefitCode;
import hongik.finEdu.points.dto.PointMonthlySummaryResponse;
import hongik.finEdu.points.dto.RedeemLedgerResult;
import hongik.finEdu.points.dto.PointRedeemRequest;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.entity.PointHistoryEntry;
import hongik.finEdu.points.entity.PointRedemption;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.repository.PointHistoryRepository;
import hongik.finEdu.points.repository.PointRedemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PointLedgerService {

    private final PointAccountRepository accountRepository;
    private final PointRedemptionRepository redemptionRepository;
    private final PointHistoryRepository historyRepository;
    private final PointActivityCacheService pointActivityCacheService;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PointAccountLocker accountLocker;

    @Value("${app.points.demo-initial-balance:0}")
    private int demoInitialBalance;

    @Transactional
    public RedeemLedgerResult applyRedemption(PointRedeemRequest request) {
        String userId = requireUserId(request.userId());
        String email = requireEmail(request.email());
        PointBenefitCode benefit = parseBenefit(request.benefitCode());
        int cost = benefit.getPointsRequired();

        PointAccount account = lockAccountWithDemo(userId);

        if (account.getBalance() < cost) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS,
                    "보유 포인트 " + account.getBalance() + "P 중 " + cost + "P가 필요합니다.");
        }

        account.debit(cost);
        accountRepository.save(account);

        String ref = UUID.randomUUID().toString();
        redemptionRepository.save(PointRedemption.builder()
                .redemptionRef(ref)
                .userId(userId)
                .email(email)
                .benefitCode(benefit)
                .pointsSpent(cost)
                .build());

        historyRepository.save(PointHistoryEntry.builder()
                .userId(userId)
                .delta(-cost)
                .title("혜택 교환: " + benefit.getBenefitName())
                .detail(benefit.getDescription())
                .relatedRef(ref)
                .build());

        scheduleActivityCacheRefresh(userId);
        return new RedeemLedgerResult(ref, userId, email, benefit, cost, account.getBalance());
    }

    /** 계정 잠금(없으면 데모 포인트와 함께 생성) */
    private PointAccount lockAccountWithDemo(String userId) {
        int initial = Math.max(0, demoInitialBalance);
        PointAccountLocker.Locked locked = accountLocker.lockOrCreate(userId, initial);
        if (locked.created() && initial > 0) {
            historyRepository.save(PointHistoryEntry.builder()
                    .userId(userId)
                    .delta(initial)
                    .title("가입/데모 포인트 지급")
                    .detail("첫 교환 시 자동 지급된 포인트입니다.")
                    .build());
        }
        return locked.account();
    }

    private void scheduleActivityCacheRefresh(String userId) {
        afterCommitExecutor.runAfterCommit("point-activity-redis",
                () -> pointActivityCacheService.refreshFromDb(userId));
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "userId는 필수입니다.");
        }
        return userId.trim();
    }

    private static String requireEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "수신 이메일은 필수입니다.");
        }
        String e = email.trim();
        if (!e.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "올바른 이메일 형식이 아닙니다.");
        }
        return e;
    }

    private static PointBenefitCode parseBenefit(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.POINT_BENEFIT_NOT_FOUND, "혜택 코드는 필수입니다.");
        }
        try {
            return PointBenefitCode.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.POINT_BENEFIT_NOT_FOUND,
                    "혜택 코드를 확인해 주세요. (예: CONVENIENCE_DISCOUNT)");
        }
    }

    @Transactional(readOnly = true)
    public int getBalance(String userId) {
        String id = requireUserId(userId);
        return accountRepository.findByUserId(id)
                .map(PointAccount::getBalance)
                .orElse(0);
    }

    @Transactional(readOnly = true)
    public PointMonthlySummaryResponse getMonthlySummary(String userId, java.time.YearMonth month) {
        String id = requireUserId(userId);
        java.time.ZoneId zone = java.time.ZoneId.of("Asia/Seoul");
        java.time.ZoneId jvm = java.time.ZoneId.systemDefault();
        java.time.LocalDateTime from = month.atDay(1).atStartOfDay(zone).withZoneSameInstant(jvm).toLocalDateTime();
        java.time.LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay(zone).withZoneSameInstant(jvm).toLocalDateTime();
        int earned = historyRepository.sumPositiveDeltaBetween(id, from, to);
        int spent = historyRepository.sumNegativeDeltaBetween(id, from, to);
        return new PointMonthlySummaryResponse(id, month, earned, Math.abs(spent), earned + spent);
    }
}
