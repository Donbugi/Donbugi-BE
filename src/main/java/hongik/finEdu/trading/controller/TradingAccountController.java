package hongik.finEdu.trading.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.trading.dto.AccountSnapshotDto;
import hongik.finEdu.trading.dto.AccountSummaryDto;
import hongik.finEdu.trading.dto.HoldingDto;
import hongik.finEdu.trading.service.TradingAccountService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class TradingAccountController {

    private final TradingAccountService tradingAccountService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<AccountSummaryDto> getAccount(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.getAccountSummary(uid));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/snapshot")
    public ResponseEntity<AccountSnapshotDto> snapshot(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.getAccountSnapshot(uid));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingDto>> getHoldings(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.listHoldings(uid));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/holdings/{stockCode}")
    public ResponseEntity<HoldingDto> getHolding(
            Authentication authentication,
            @RequestParam(required = false) String userId,
            @PathVariable String stockCode) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.getHolding(uid, stockCode));
    }
}
