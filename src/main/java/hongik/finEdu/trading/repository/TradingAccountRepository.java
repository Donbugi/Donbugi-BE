package hongik.finEdu.trading.repository;

import hongik.finEdu.trading.entity.TradingAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TradingAccountRepository extends JpaRepository<TradingAccount, Long> {
    Optional<TradingAccount> findByUserId(String userId);
}
