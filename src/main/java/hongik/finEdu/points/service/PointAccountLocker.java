package hongik.finEdu.points.service;

import hongik.finEdu.points.entity.PointAccount;
import hongik.finEdu.points.repository.PointAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포인트 계정 "생성 또는 잠금" 공통 로직.
 * <ul>
 *   <li>INSERT ... ON CONFLICT DO NOTHING 으로 동시 첫 요청 race 에서도 unique 위반이 나지 않는다.</li>
 *   <li>이후 SELECT ... FOR UPDATE 로 행을 잠가 잔액 갱신을 직렬화한다 (lost update 방지).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PointAccountLocker {

    private final PointAccountRepository accountRepository;

    public record Locked(PointAccount account, boolean created) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Locked lockOrCreate(String userId, int initialBalance) {
        int inserted = accountRepository.insertIfAbsent(userId, Math.max(0, initialBalance));
        PointAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("point_account 생성 직후 조회 실패: " + userId));
        return new Locked(account, inserted == 1);
    }
}
