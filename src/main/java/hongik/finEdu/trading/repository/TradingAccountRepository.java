package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.TradingAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, Long> {

    Optional<TradingAccount> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM TradingAccount a WHERE a.userId = :userId")
    Optional<TradingAccount> findByUserIdForUpdate(@Param("userId") String userId);

    /** 동시 첫 주문에도 안전한 계좌 개설. 새로 만들었으면 1 */
    @Modifying
    @Query(value = """
            INSERT INTO trading_account (user_id, cash, locked_cash, created_at, version)
            VALUES (:userId, :cash, 0, :createdAt, 0)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId,
                       @Param("cash") BigDecimal cash,
                       @Param("createdAt") LocalDateTime createdAt);

    @Query("SELECT a.userId FROM TradingAccount a")
    List<String> findAllUserIds();
}
