package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.TradingHolding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TradingHoldingRepository extends JpaRepository<TradingHolding, Long> {
    List<TradingHolding> findByUserIdOrderByStockCodeAsc(String userId);

    Optional<TradingHolding> findByUserIdAndStockCode(String userId, String stockCode);
}
