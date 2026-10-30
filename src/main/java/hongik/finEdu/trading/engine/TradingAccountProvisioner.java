package hongik.finEdu.trading.engine;

import hongik.finEdu.trading.domain.TradingConstants;
import hongik.finEdu.trading.entity.CashLedgerEntry;
import hongik.finEdu.trading.entity.TradingAccount;
import hongik.finEdu.trading.repository.CashLedgerRepository;
import hongik.finEdu.trading.repository.TradingAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 계좌 "개설 또는 잠금".
 * INSERT ... ON CONFLICT DO NOTHING → 동시 첫 주문에도 unique 위반 없음.
 * 새로 개설되면 원장에 OPENING(초기 자금) 기록.
 */
@Component
@RequiredArgsConstructor
public class TradingAccountProvisioner {

    private final TradingAccountRepository accountRepository;
    private final CashLedgerRepository ledgerRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public TradingAccount lockOrCreate(String userId) {
        int created = accountRepository.insertIfAbsent(userId, TradingConstants.INITIAL_CASH, LocalDateTime.now());
        TradingAccount account = accountRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("trading_account 생성 직후 조회 실패: " + userId));
        if (created == 1) {
            ledgerRepository.save(CashLedgerEntry.builder()
                    .userId(userId)
                    .kind(CashLedgerEntry.Kind.OPENING)
                    .delta(TradingConstants.INITIAL_CASH)
                    .balanceAfter(account.getCash())
                    .build());
        }
        return account;
    }
}
