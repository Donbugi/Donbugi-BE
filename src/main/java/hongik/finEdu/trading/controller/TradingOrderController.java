package hongik.finEdu.trading.controller;

import hongik.finEdu.auth.support.AuthUserResolver;
import hongik.finEdu.config.OpenApiConfig;
import hongik.finEdu.config.OpenApiParams;
import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.trading.dto.LimitOrderRequest;
import hongik.finEdu.trading.dto.LimitOrderResponseDto;
import hongik.finEdu.trading.dto.MarketOrderRequest;
import hongik.finEdu.trading.dto.MarketOrderResponseDto;
import hongik.finEdu.trading.dto.OrderModifyRequest;
import hongik.finEdu.trading.dto.OrderPreviewRequest;
import hongik.finEdu.trading.dto.OrderPreviewResponse;
import hongik.finEdu.trading.dto.PendingOrderDto;
import hongik.finEdu.trading.service.TradingOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = OpenApiTags.TRADING, description = "주문·정정·취소·미체결")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradingOrderController {

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final TradingOrderService tradingOrderService;
    private final AuthUserResolver authUserResolver;

    @Operation(
            summary = "주문 미리보기",
            description = """
                    수수료(원 미만 절사)·거래세(2026년 0.20%)·예상 금액 계산.
                    limitPrice 생략 시 현재 호가를 소진했을 때의 예상 체결가(시장가). 상·하한가, 호가단위, 주문가능금액 포함.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/preview")
    public ResponseEntity<OrderPreviewResponse> preview(
            Authentication authentication,
            @RequestBody OrderPreviewRequest request) {
        String uid = authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.previewOrder(uid, request));
    }

    @Operation(
            summary = "시장가 주문",
            description = """
                    09:00~15:20 접속매매: 호가를 소진하며 즉시 체결, 호가 잔량을 넘는 수량은 취소(IOC).
                    08:30~09:00 / 15:20~15:30 동시호가: 접수 후 시가/종가 단일가로 일괄 체결 (매수는 상한가 기준 금액 lock).
                    그 외 시간·휴장일 TR004. Idempotency-Key 헤더로 중복 주문 방지.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/market")
    public ResponseEntity<MarketOrderResponseDto> marketOrder(
            Authentication authentication,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody MarketOrderRequest request) {
        String uid = authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.placeMarketOrder(uid, request, idempotencyKey));
    }

    @Operation(
            summary = "지정가 주문",
            description = """
                    호가단위(TR009)·가격제한폭 ±30%(TR010) 검증. 매수는 주문금액+수수료를 lock, 매도는 수량을 lock.
                    접속매매 중 즉시 체결 가능한 가격이면 접수와 동시에 체결(부분체결 가능), 나머지는 대기.""")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PostMapping("/limit")
    public ResponseEntity<LimitOrderResponseDto> limitOrder(
            Authentication authentication,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @RequestBody LimitOrderRequest request) {
        String uid = authUserResolver.requireMatchingUserId(authentication, request.userId());
        return ResponseEntity.ok(tradingOrderService.placeLimitOrder(uid, request, idempotencyKey));
    }

    @Operation(summary = "정정 주문", description = "미체결 지정가 주문의 가격·수량 정정 (잔량 취소 + 재주문, 원자적).")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @PatchMapping("/{orderId}")
    public ResponseEntity<LimitOrderResponseDto> modifyOrder(
            Authentication authentication,
            @RequestHeader(value = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Parameter(description = "주문 ID", example = "1") @PathVariable Long orderId,
            @RequestBody OrderModifyRequest request) {
        String uid = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(tradingOrderService.modifyOrder(
                uid, orderId, request.limitPrice(), request.quantity(), idempotencyKey));
    }

    @Operation(summary = "미체결 주문 취소", description = "잔량 취소. 묶인 금액/수량이 해제된다. 일부 체결분은 유지.")
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

    @Operation(summary = "미체결 주문 목록", description = "PENDING + PARTIALLY_FILLED")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping("/pending")
    public ResponseEntity<List<PendingOrderDto>> pendingOrders(
            Authentication authentication,
            @OpenApiParams.UserIdQuery @RequestParam(required = false) String userId) {
        String uid = authUserResolver.requireMatchingUserId(authentication, userId);
        return ResponseEntity.ok(tradingOrderService.listPendingOrders(uid));
    }

    @Operation(summary = "주문 내역 (페이지)", description = "모든 상태의 주문, 최신순. size 최대 100.")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @GetMapping
    public ResponseEntity<Page<PendingOrderDto>> orders(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String uid = authUserResolver.requireUserId(authentication);
        return ResponseEntity.ok(tradingOrderService.listOrders(uid, page, size));
    }
}
