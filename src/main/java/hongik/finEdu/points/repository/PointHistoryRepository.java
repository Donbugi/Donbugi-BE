package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntry, Long> {

    List<PointHistoryEntry> findByUserIdOrderByOccurredAtDesc(String userId, Pageable pageable);
}
