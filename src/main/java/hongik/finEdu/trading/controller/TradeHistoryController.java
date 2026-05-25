package hongik.finEdu.trading.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.trading.dto.TradeHistoryDto;
import hongik.finEdu.trading.service.TradingAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = OpenApiTags.TRADING, description = "체결 내역")
@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeHistoryController {

    private final TradingAccountService tradingAccountService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "체결 내역 (페이지)",
            description = "최신순. stockCode로 종목 필터 가능. size 최대 100.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<Page<TradeHistoryDto>> listTrades(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId,
            @Parameter(description = "페이지 (0부터)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "종목코드 필터", example = "005930")
            @RequestParam(required = false) String stockCode) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.listTrades(uid, page, size, stockCode));
    }
}
