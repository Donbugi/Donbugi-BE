package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.TradeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {
    Page<TradeHistory> findByUserIdOrderByTradedAtDesc(String userId, Pageable pageable);

    Page<TradeHistory> findByUserIdAndStockCodeOrderByTradedAtDesc(
            String userId, String stockCode, Pageable pageable);
}
