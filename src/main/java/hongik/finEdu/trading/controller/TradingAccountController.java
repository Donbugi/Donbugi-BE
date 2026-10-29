package hongik.finEdu.trading.controller;

import hongik.finEdu.trading.dto.AssetHistoryPointDto;
import hongik.finEdu.trading.dto.RankingResponse;
import hongik.finEdu.trading.stream.TradingEventStream;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.trading.dto.AccountSnapshotDto;
import hongik.finEdu.trading.dto.AccountSummaryDto;
import hongik.finEdu.trading.dto.HoldingDto;
import hongik.finEdu.trading.service.TradingAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = OpenApiTags.TRADING, description = "계좌·보유")
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class TradingAccountController {

    private final TradingAccountService tradingAccountService;
    private final AuthUserResolver authUserResolver;
    private final TradingEventStream tradingEventStream;

    @Operation(
            summary = "계좌 요약",
            description = "예수금·주문가능금액·묶인 금액·평가금·총자산·실현/평가 손익. 미개설 시 초기 1000만원 기준.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<AccountSummaryDto> getAccount(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.getAccountSummary(uid));
    }

    @Operation(
            summary = "계좌 스냅샷 (FE broker용)",
            description = "summary + holdings + 전 종목 quotes + marketOpen 한 번에.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/snapshot")
    public ResponseEntity<AccountSnapshotDto> snapshot(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.getAccountSnapshot(uid));
    }

    @Operation(summary = "보유 종목 목록")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingDto>> getHoldings(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.listHoldings(uid));
    }

    @Operation(summary = "보유 종목 단건")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/holdings/{stockCode}")
    public ResponseEntity<HoldingDto> getHolding(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId,
            @Parameter(description = "종목코드 6자리", example = "005930")
            @PathVariable String stockCode) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingAccountService.getHolding(uid, stockCode));
    }

    @Operation(summary = "자산 추이", description = "장 마감(15:40) 스냅샷 기준 일별 총자산·수익률. days 기본 30, 최대 365.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/asset-history")
    public ResponseEntity<List<AssetHistoryPointDto>> assetHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "30") int days) {
        String uid = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(tradingAccountService.getAssetHistory(uid, days));
    }

    @Operation(summary = "수익률 랭킹", description = "최근 장 마감 기준 상위 N명 + 내 순위. top 기본 20.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/ranking")
    public ResponseEntity<RankingResponse> ranking(
            Authentication authentication,
            @RequestParam(defaultValue = "20") int top) {
        String uid = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(tradingAccountService.getRanking(uid, top));
    }

    @Operation(
            summary = "체결 알림 스트림 (SSE)",
            description = "text/event-stream. event=trade 로 체결 내역 JSON 푸시. Authorization 헤더 필요(fetch 기반 SSE 클라이언트 사용).")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        String uid = authUserResolver.requireUserId(authentication);
        return tradingEventStream.subscribe(uid);
    }
}
