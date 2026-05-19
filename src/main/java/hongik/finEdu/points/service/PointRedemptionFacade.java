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
    private final PointRewardEmailService rewardEmailService;

    public PointRedeemResponseDto redeem(PointRedeemRequest request) {
        RedeemLedgerResult ledger = ledgerService.applyRedemption(request);
        boolean mailSent = rewardEmailService.sendRedemptionNotice(ledger);
        String mailNotice = mailSent
                ? "등록하신 이메일로 혜택 안내 메일을 발송했습니다. QR코드는 별도 메일로 안내됩니다."
                : "메일 발송에 실패했거나 SMTP가 설정되지 않았습니다. 서버 로그를 확인하거나 spring.mail 설정 후 다시 시도해 주세요.";

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
