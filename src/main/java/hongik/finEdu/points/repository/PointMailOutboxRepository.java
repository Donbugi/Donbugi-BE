package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointMailOutbox;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointMailOutboxRepository extends JpaRepository<PointMailOutbox, Long> {

    @Query("""
            SELECT o.id FROM PointMailOutbox o
            WHERE o.status = hongik.finEdu.points.entity.PointMailOutbox.Status.PENDING AND o.nextAttemptAt <= :now
            ORDER BY o.id
            """)
    List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM PointMailOutbox o WHERE o.id = :id")
    Optional<PointMailOutbox> findByIdForUpdate(@Param("id") Long id);

    Optional<PointMailOutbox> findByRedemptionRef(String redemptionRef);
}
