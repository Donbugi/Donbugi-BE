package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.TradeHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory, Long> {

    Page<TradeHistory> findByUserIdOrderByTradedAtDesc(String userId, Pageable pageable);

    Page<TradeHistory> findByUserIdAndStockCodeOrderByTradedAtDesc(
            String userId, String stockCode, Pageable pageable);

    List<TradeHistory> findByOrderIdOrderByIdAsc(Long orderId);

    @Query("SELECT COALESCE(SUM(t.realizedPnl), 0) FROM TradeHistory t WHERE t.userId = :userId")
    BigDecimal sumRealizedPnl(@Param("userId") String userId);

    /** 정합성 검증용: 종목별 순매수 수량 (매수 − 매도) */
    @Query("""
            SELECT t.stockCode,
                   SUM(CASE WHEN t.orderType = hongik.finEdu.trading.domain.OrderType.BUY
                            THEN t.quantity ELSE -t.quantity END)
            FROM TradeHistory t
            WHERE t.userId = :userId
            GROUP BY t.stockCode
            """)
    List<Object[]> netQuantityByStock(@Param("userId") String userId);
}
