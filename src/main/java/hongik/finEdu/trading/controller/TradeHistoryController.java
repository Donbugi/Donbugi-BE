package hongik.finEdu.trading.controller;

import hongik.finEdu.trading.dto.TradeHistoryDto;
import hongik.finEdu.trading.service.TradingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trades")
@RequiredArgsConstructor
public class TradeHistoryController {

    private final TradingAccountService tradingAccountService;

    @GetMapping
    public ResponseEntity<Page<TradeHistoryDto>> listTrades(
            @RequestParam String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String stockCode) {
        return ResponseEntity.ok(tradingAccountService.listTrades(userId, page, size, stockCode));
    }
}
