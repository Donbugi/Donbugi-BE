package hongik.finEdu.points.service;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.points.dto.policy.PointAwardResult;
import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.entity.PointHistoryEntry;
import hongik.finEdu.points.entity.PointRewardClaim;
import hongik.finEdu.points.repository.PointAccountRepository;
import hongik.finEdu.points.repository.PointHistoryRepository;
import hongik.finEdu.points.repository.PointRewardClaimRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PointRewardService {

    private final PointRewardClaimRepository claimRepository;
    private final PointAccountRepository accountRepository;
    private final PointHistoryRepository historyRepository;
    private final AfterCommitExecutor afterCommitExecutor;
    private final PointActivityCacheService pointActivityCacheService;

    /**
     * 동일 {@code claimKey} 는 전역 1회만 지급. 이미 있으면 {@code awarded=false}.
     */
    @Transactional
    public PointAwardResult tryAwardOnce(String userId, String claimKey, int points, String title, String detail) {
        if (claimKey == null || claimKey.isBlank() || claimKey.length() > 210) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "claimKey가 유효하지 않습니다.");
        }
        if (points <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "포인트는 양수여야 합니다.");
        }
        if (claimRepository.existsByClaimKey(claimKey)) {
            int bal = accountRepository.findByUserId(userId).map(PointAccount::getBalance).orElse(0);
            return new PointAwardResult(false, 0, bal);
        }

        claimRepository.save(PointRewardClaim.builder()
                .userId(userId)
                .claimKey(claimKey)
                .points(points)
                .title(title)
                .build());

        PointAccount acc = accountRepository.findByUserId(userId)
                .orElseGet(() -> accountRepository.save(PointAccount.builder()
                        .userId(userId)
                        .balance(0)
                        .build()));
        acc.credit(points);
        accountRepository.save(acc);

        historyRepository.save(PointHistoryEntry.builder()
                .userId(userId)
                .delta(points)
                .title(title)
                .detail(detail)
                .relatedRef(claimKey)
                .build());

        afterCommitExecutor.runAfterCommit("point-activity-redis",
                () -> pointActivityCacheService.refreshFromDb(userId));
        return new PointAwardResult(true, points, acc.getBalance());
    }
}
