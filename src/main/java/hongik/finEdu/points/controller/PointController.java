package hongik.finEdu.points.controller;

import hongik.finEdu.points.domain.PointBenefitCode;
import hongik.finEdu.points.dto.PointBalanceResponseDto;
import hongik.finEdu.points.dto.PointBenefitItemDto;
import hongik.finEdu.points.dto.PointRedeemRequest;
import hongik.finEdu.points.dto.PointRedeemResponseDto;
import hongik.finEdu.points.service.PointLedgerService;
import hongik.finEdu.points.service.PointRedemptionFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PointController {

    private final PointLedgerService ledgerService;
    private final PointRedemptionFacade redemptionFacade;

    /** GET /api/point-benefits — 교환 가능 혜택 목록(표와 동일 기준) */
    @GetMapping("/api/point-benefits")
    public ResponseEntity<List<PointBenefitItemDto>> listBenefits() {
        List<PointBenefitItemDto> list = Arrays.stream(PointBenefitCode.values())
                .map(b -> new PointBenefitItemDto(
                        b,
                        b.getPointsRequired(),
                        b.getBenefitName(),
                        b.getDescription()))
                .toList();
        return ResponseEntity.ok(list);
    }

    /** GET /api/points/balance?userId= — 잔여 포인트 (미가입 시 0) */
    @GetMapping("/api/points/balance")
    public ResponseEntity<PointBalanceResponseDto> balance(@RequestParam String userId) {
        int b = ledgerService.getBalance(userId);
        return ResponseEntity.ok(new PointBalanceResponseDto(userId.trim(), b));
    }

    /**
     * POST /api/points/redeem
     * 본문: { "userId", "email", "benefitCode": "CONVENIENCE_DISCOUNT" 등 }
     */
    @PostMapping("/api/points/redeem")
    public ResponseEntity<PointRedeemResponseDto> redeem(@RequestBody PointRedeemRequest request) {
        return ResponseEntity.ok(redemptionFacade.redeem(request));
    }
}
