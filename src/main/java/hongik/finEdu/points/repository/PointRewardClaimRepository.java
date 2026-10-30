package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointRewardClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface PointRewardClaimRepository extends JpaRepository<PointRewardClaim, Long> {

    boolean existsByClaimKey(String claimKey);

    /**
     * claim 선점. 이미 같은 claimKey 가 있으면 0을 반환한다.
     * <p>예외(unique 위반) 대신 ON CONFLICT 를 쓰는 이유: 예외가 나면 바깥 트랜잭션이 rollback-only 로
     * 표시되어 커밋 시점에 UnexpectedRollbackException 이 터진다.</p>
     */
    @Modifying
    @Query(value = """
            INSERT INTO point_reward_claim (user_id, claim_key, points, title, created_at)
            VALUES (:userId, :claimKey, :points, :title, :createdAt)
            ON CONFLICT (claim_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId,
                       @Param("claimKey") String claimKey,
                       @Param("points") int points,
                       @Param("title") String title,
                       @Param("createdAt") LocalDateTime createdAt);
}
