package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.domain.OrderStatus;
import hongik.finEdu.trading.entity.TradingOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TradingOrderRepository extends JpaRepository<TradingOrder, Long> {

    Optional<TradingOrder> findByIdAndUserId(Long id, String userId);

    Optional<TradingOrder> findByUserIdAndClientOrderId(String userId, String clientOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM TradingOrder o WHERE o.id = :id")
    Optional<TradingOrder> findByIdForUpdate(@Param("id") Long id);

    List<TradingOrder> findByUserIdAndStatusInOrderByCreatedAtDesc(String userId, Collection<OrderStatus> statuses);

    Page<TradingOrder> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /** 체결 엔진용: 종목별 미체결 주문 (시간 우선) */
    @Query("""
            SELECT o FROM TradingOrder o
            WHERE o.stockCode = :stockCode AND o.status IN :statuses
            ORDER BY o.createdAt ASC, o.id ASC
            """)
    List<TradingOrder> findOpenByStock(@Param("stockCode") String stockCode,
                                       @Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT o.id FROM TradingOrder o WHERE o.status IN :statuses ORDER BY o.createdAt ASC, o.id ASC")
    List<Long> findOpenIds(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("SELECT DISTINCT o.stockCode FROM TradingOrder o WHERE o.status IN :statuses")
    List<String> findStocksWithOpenOrders(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
            SELECT COALESCE(SUM(o.lockedAmount), 0) FROM TradingOrder o
            WHERE o.userId = :userId AND o.status IN :statuses
            """)
    java.math.BigDecimal sumLockedAmount(@Param("userId") String userId,
                                         @Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
            SELECT COALESCE(SUM(o.quantity - o.filledQuantity), 0) FROM TradingOrder o
            WHERE o.userId = :userId AND o.stockCode = :stockCode
              AND o.orderType = hongik.finEdu.trading.domain.OrderType.SELL AND o.status IN :statuses
            """)
    long sumOpenSellQuantity(@Param("userId") String userId,
                             @Param("stockCode") String stockCode,
                             @Param("statuses") Collection<OrderStatus> statuses);
}
