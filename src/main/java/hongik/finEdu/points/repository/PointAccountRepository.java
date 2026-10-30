package hongik.finEdu.points.repository;

import hongik.finEdu.points.entity.PointAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PointAccountRepository extends JpaRepository<PointAccount, Long> {

    Optional<PointAccount> findByUserId(String userId);

    /** 잔액 변경 전 행 잠금 (SELECT ... FOR UPDATE) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM PointAccount a WHERE a.userId = :userId")
    Optional<PointAccount> findByUserIdForUpdate(@Param("userId") String userId);

    /** 동시 첫 요청에도 안전한 계정 생성. 새로 만들었으면 1, 이미 있으면 0. */
    @Modifying
    @Query(value = """
            INSERT INTO point_account (user_id, balance, version)
            VALUES (:userId, :balance, 0)
            ON CONFLICT (user_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") String userId, @Param("balance") int balance);

    @Query("SELECT a.userId FROM PointAccount a")
    List<String> findAllUserIds();
}
