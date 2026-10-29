package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.CashLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface CashLedgerRepository extends JpaRepository<CashLedgerEntry, Long> {

    @Query("SELECT COALESCE(SUM(c.delta), 0) FROM CashLedgerEntry c WHERE c.userId = :userId")
    BigDecimal sumDelta(@Param("userId") String userId);

    boolean existsByUserId(String userId);
}
