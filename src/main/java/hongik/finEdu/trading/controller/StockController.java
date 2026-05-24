package hongik.finEdu.trading.controller;

import hongik.finEdu.trading.dto.StockDetailDto;
import hongik.finEdu.trading.dto.StockSummaryDto;
import hongik.finEdu.trading.service.TradingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final TradingAccountService tradingAccountService;

    @GetMapping
    public ResponseEntity<List<StockSummaryDto>> listStocks() {
        return ResponseEntity.ok(tradingAccountService.listStocks());
    }

    @GetMapping("/{stockCode}")
    public ResponseEntity<StockDetailDto> getStock(@PathVariable String stockCode) {
        return ResponseEntity.ok(tradingAccountService.getStockDetail(stockCode));
    }
}
