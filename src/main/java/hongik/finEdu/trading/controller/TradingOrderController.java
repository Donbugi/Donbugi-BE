package hongik.finEdu.trading.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.trading.dto.*;
import hongik.finEdu.trading.service.TradingOrderService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradingOrderController {

    private final TradingOrderService tradingOrderService;
    private final AuthUserResolver authUserResolver;

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/preview")
    public ResponseEntity<OrderPreviewResponse> preview(
            Authentication authentication,
            @RequestBody OrderPreviewRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.previewOrder(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/market")
    public ResponseEntity<MarketOrderResponseDto> marketOrder(
            Authentication authentication,
            @RequestBody MarketOrderRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.placeMarketOrder(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/limit")
    public ResponseEntity<LimitOrderResponseDto> limitOrder(
            Authentication authentication,
            @RequestBody LimitOrderRequest request) {
        authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.placeLimitOrder(request));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> cancelOrder(
            Authentication authentication,
            @RequestParam(required = false) String userId,
            @PathVariable Long orderId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        tradingOrderService.cancelOrder(uid, orderId);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/pending")
    public ResponseEntity<List<PendingOrderDto>> pendingOrders(
            Authentication authentication,
            @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingOrderService.listPendingOrders(uid));
    }
}
