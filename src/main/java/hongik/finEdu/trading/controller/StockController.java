package hongik.finEdu.trading.controller;

import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.trading.dto.StockDetailDto;
import hongik.finEdu.trading.dto.StockSummaryDto;
import hongik.finEdu.trading.service.TradingAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = OpenApiTags.TRADING, description = "종목 시세")
@RestController
@RequestMapping("/api/stocks")
@RequiredArgsConstructor
public class StockController {

    private final TradingAccountService tradingAccountService;

    @Operation(
            summary = "모의투자 종목 20개 목록",
            description = "삼성전자 등 고정 20종목 현재가·등락률. 인증 불필요.")
    @GetMapping
    public ResponseEntity<List<StockSummaryDto>> listStocks() {
        return ResponseEntity.ok(tradingAccountService.listStocks());
    }

    @Operation(summary = "종목 상세 시세", description = "OHLCV·등락률·장 상태. stockCode 6자리.")
    @GetMapping("/{stockCode}")
    public ResponseEntity<StockDetailDto> getStock(
            @Parameter(description = "종목코드", example = "005930")
            @PathVariable String stockCode) {
        return ResponseEntity.ok(tradingAccountService.getStockDetail(stockCode));
    }
}
