package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByUserId(String userId);
}
