package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointRedemptionRepository extends JpaRepository<PointRedemption, Long> {

    Optional<PointRedemption> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
}
