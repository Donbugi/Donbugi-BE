package hongik.finEdu.trading.controller;

import hongik.finEdu.trading.dto.LimitOrderRequest;
import hongik.finEdu.trading.dto.LimitOrderResponseDto;
import hongik.finEdu.trading.dto.MarketOrderRequest;
import hongik.finEdu.trading.dto.MarketOrderResponseDto;
import hongik.finEdu.trading.dto.PendingOrderDto;
import hongik.finEdu.trading.service.TradingOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class TradingOrderController {

    private final TradingOrderService tradingOrderService;

    @PostMapping("/market")
    public ResponseEntity<MarketOrderResponseDto> marketOrder(@RequestBody MarketOrderRequest request) {
        return ResponseEntity.ok(tradingOrderService.placeMarketOrder(request));
    }

    @PostMapping("/limit")
    public ResponseEntity<LimitOrderResponseDto> limitOrder(@RequestBody LimitOrderRequest request) {
        return ResponseEntity.ok(tradingOrderService.placeLimitOrder(request));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> cancelOrder(
            @RequestParam String userId,
            @PathVariable Long orderId) {
        tradingOrderService.cancelOrder(userId, orderId);
        return ResponseEntity.ok(Map.of("status", "cancelled"));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingOrderDto>> pendingOrders(@RequestParam String userId) {
        return ResponseEntity.ok(tradingOrderService.listPendingOrders(userId));
    }
}
