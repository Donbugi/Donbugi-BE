package hongik.finEdu.trading.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.trading.dto.*;
import hongik.finEdu.trading.service.TradingOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = OpenApiTags.TRADING, description = "주문·미체결")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradingOrderController {

    private final TradingOrderService tradingOrderService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "주문 미리보기",
            description = "체결 전 수수료·세금·예상 금액 계산. limitPrice 생략 시 현재가 사용.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/preview")
    public ResponseEntity<OrderPreviewResponse> preview(
            Authentication authentication,
            @RequestBody OrderPreviewRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.previewOrder(request));
    }

    @Operation(summary = "시장가 주문", description = "장 중(09:00~15:30)만 가능. TR004=장 마감.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/market")
    public ResponseEntity<MarketOrderResponseDto> marketOrder(
            Authentication authentication,
            @RequestBody MarketOrderRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.placeMarketOrder(request));
    }

    @Operation(summary = "지정가 주문", description = "지정가 ≠ 현재가. 체결 시까지 pending.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/limit")
    public ResponseEntity<LimitOrderResponseDto> limitOrder(
            Authentication authentication,
            @RequestBody LimitOrderRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.placeLimitOrder(request));
    }

    @Operation(summary = "미체결 주문 취소")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> cancelOrder(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId,
            @Parameter(description = "주문 ID", example = "1") @PathVariable Long orderId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        tradingOrderService.cancelOrder(uid, orderId);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @Operation(summary = "미체결 주문 목록")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/pending")
    public ResponseEntity<List<PendingOrderDto>> pendingOrders(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingOrderService.listPendingOrders(uid));
    }
}
