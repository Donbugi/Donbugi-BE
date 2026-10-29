package hongik.finEdu.points.service;

import hongik.finEdu.points.dto.PointRedeemRequest;
import hongik.finEdu.points.dto.PointRedeemResponseDto;
import hongik.finEdu.points.dto.RedeemLedgerResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 포인트 차감(DB 커밋) 이후 교환 안내 메일을 보낸다.
 */
@Service
@RequiredArgsConstructor
public class PointRedemptionFacade {

    private final PointLedgerService ledgerService;

    /**
     * 포인트 차감·교환 기록·메일 outbox 를 한 트랜잭션으로 처리하고, 메일은 커밋 후 비동기 발송한다.
     * mailSent 는 항상 false(발송 대기열 등록)이며 실제 발송은 수 초 내 진행된다.
     */
    public PointRedeemResponseDto redeem(PointRedeemRequest request, String idempotencyKey) {
        RedeemLedgerResult ledger = ledgerService.applyRedemption(request, idempotencyKey);
        boolean mailSent = false;
        String mailNotice = ledger.replayed()
                ? "이미 처리된 교환 요청입니다. (같은 Idempotency-Key)"
                : "교환이 완료되었습니다. 안내 메일은 잠시 후 발송됩니다. QR코드는 별도 메일로 안내됩니다.";

        return new PointRedeemResponseDto(
                ledger.redemptionRef(),
                ledger.userId(),
                ledger.email(),
                ledger.benefitCode(),
                ledger.benefitCode().getBenefitName(),
                ledger.pointsSpent(),
                ledger.balanceAfter(),
                mailSent,
                mailNotice
        );
    }
}
