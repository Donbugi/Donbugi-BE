package hongik.finEdu.points.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.points.domain.PointBenefitCode;
import hongik.finEdu.points.dto.PointActivityItemDto;
import hongik.finEdu.points.dto.PointBalanceResponseDto;
import hongik.finEdu.points.dto.PointBenefitItemDto;
import hongik.finEdu.points.dto.PointEarnRequest;
import hongik.finEdu.points.dto.PointEarnResponseDto;
import hongik.finEdu.points.dto.PointMonthlySummaryResponse;
import hongik.finEdu.points.dto.PointRedeemRequest;
import hongik.finEdu.points.dto.PointRedeemResponseDto;
import hongik.finEdu.points.service.PointActivityCacheService;
import hongik.finEdu.points.service.PointLedgerService;
import hongik.finEdu.points.service.PointRedemptionFacade;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class PointController {

    private final PointLedgerService ledgerService;
    private final PointRedemptionFacade redemptionFacade;
    private final PointActivityCacheService pointActivityCacheService;
    private final AuthUserResolver authUserResolver;

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

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/api/points/balance")
    public ResponseEntity<PointBalanceResponseDto> balance(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(new PointBalanceResponseDto(uid, ledgerService.getBalance(uid)));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/api/points/earn")
    public ResponseEntity<PointEarnResponseDto> earn(
            Authentication authentication,
            @RequestBody PointEarnRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(ledgerService.earn(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/api/points/recent-activity")
    public ResponseEntity<List<PointActivityItemDto>> recentActivity(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(pointActivityCacheService.getRecent(uid));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/api/points/redeem")
    public ResponseEntity<PointRedeemResponseDto> redeem(
            Authentication authentication,
            @RequestBody PointRedeemRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(redemptionFacade.redeem(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/api/points/monthly-summary")
    public ResponseEntity<PointMonthlySummaryResponse> monthlySummary(
            Authentication authentication,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        YearMonth ym = resolveYearMonth(year, month);
        return ResponseEntity.ok(ledgerService.getMonthlySummary(uid, ym));
    }

    private static YearMonth resolveYearMonth(Integer year, Integer month) {
        if (year == null && month == null) {
            return YearMonth.now();
        }
        if (year == null || month == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "year와 month는 함께 지정하거나 둘 다 생략해 주세요. (예: year=2026&month=5)");
        }
        try {
            return YearMonth.of(year, month);
        } catch (DateTimeException e) {
            throw new BusinessException(
                    ErrorCode.INVALID_INPUT,
                    "유효한 year·month를 입력해 주세요.");
        }
    }
}
