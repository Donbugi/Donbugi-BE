package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.DailyAssetSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyAssetSnapshotRepository extends JpaRepository<DailyAssetSnapshot, Long> {

    Optional<DailyAssetSnapshot> findByUserIdAndSnapshotDate(String userId, LocalDate snapshotDate);

    List<DailyAssetSnapshot> findByUserIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            String userId, LocalDate from, LocalDate to);
}
