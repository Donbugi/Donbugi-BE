package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.TradingHolding;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TradingHoldingRepository extends JpaRepository<TradingHolding, Long> {

    List<TradingHolding> findByUserIdOrderByStockCodeAsc(String userId);

    Optional<TradingHolding> findByUserIdAndStockCode(String userId, String stockCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM TradingHolding h WHERE h.userId = :userId AND h.stockCode = :stockCode")
    Optional<TradingHolding> findForUpdate(@Param("userId") String userId, @Param("stockCode") String stockCode);
}
