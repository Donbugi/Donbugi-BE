package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointRewardClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PointRewardClaimRepository extends JpaRepository<PointRewardClaim, Long> {

    boolean existsByClaimKey(String claimKey);
}
