package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointHistoryEntry;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PointHistoryRepository extends JpaRepository<PointHistoryEntry, Long> {

    List<PointHistoryEntry> findByUserIdOrderByOccurredAtDesc(String userId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(h.delta), 0) FROM PointHistoryEntry h
            WHERE h.userId = :userId AND h.delta > 0
            AND h.occurredAt >= :from AND h.occurredAt < :to
            """)
    int sumPositiveDeltaBetween(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(h.delta), 0) FROM PointHistoryEntry h
            WHERE h.userId = :userId AND h.delta < 0
            AND h.occurredAt >= :from AND h.occurredAt < :to
            """)
    int sumNegativeDeltaBetween(
            @Param("userId") String userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
