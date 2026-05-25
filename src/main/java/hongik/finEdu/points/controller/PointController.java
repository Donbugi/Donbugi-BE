package hongik.finEdu.points.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = OpenApiTags.POINTS, description = "포인트 잔액·적립·교환·내역")
@RestController
@RequiredArgsConstructor
public class PointController {

    private final PointLedgerService ledgerService;
    private final PointRedemptionFacade redemptionFacade;
    private final PointActivityCacheService pointActivityCacheService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "교환 가능 혜택 목록",
            description = "포인트 상점에 표시할 혜택 코드·필요 포인트·설명. 인증 불필요.")
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

    @Operation(summary = "포인트 잔액 조회", description = "FinIQ 포인트 잔액. 계정 없으면 0.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/api/points/balance")
    public ResponseEntity<PointBalanceResponseDto> balance(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(new PointBalanceResponseDto(uid, ledgerService.getBalance(uid)));
    }

    @Operation(summary = "포인트 수동 적립", description = "범용 적립 API. title 필수, amount ≥ 1.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/api/points/earn")
    public ResponseEntity<PointEarnResponseDto> earn(
            Authentication authentication,
            @RequestBody PointEarnRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(ledgerService.earn(request));
    }

    @Operation(
            summary = "최근 포인트 내역",
            description = "적립·사용 합쳐 최신 3건 (Redis 캐시 우선). 마이페이지 '내역'용.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/api/points/recent-activity")
    public ResponseEntity<List<PointActivityItemDto>> recentActivity(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(pointActivityCacheService.getRecent(uid));
    }

    @Operation(
            summary = "혜택 교환",
            description = """
                    benefitCode로 포인트 차감 후 교환 처리. 이메일 발송(메일 설정 시).
                    - 잔액 부족 시 400 P002
                    - benefitCode 예: CONVENIENCE_DISCOUNT, COFFEE_COUPON""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/api/points/redeem")
    public ResponseEntity<PointRedeemResponseDto> redeem(
            Authentication authentication,
            @RequestBody PointRedeemRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(redemptionFacade.redeem(request));
    }

    @Operation(
            summary = "월간 포인트 요약",
            description = """
                    지정 월(Asia/Seoul)의 적립·사용·순합계.
                    - year, month **둘 다 생략** → 이번 달
                    - **둘 다 지정** → 예: year=2026&month=5
                    - 하나만 넣으면 400 (month=4 단독 불가)""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/api/points/monthly-summary")
    public ResponseEntity<PointMonthlySummaryResponse> monthlySummary(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId,
            @OpenApiParams.YearQuery @RequestParam(required = false) Integer year,
            @OpenApiParams.MonthQuery @RequestParam(required = false) Integer month) {
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
