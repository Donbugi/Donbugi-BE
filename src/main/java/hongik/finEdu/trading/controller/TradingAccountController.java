package hongik.finEdu.trading.controller;

import hongik.finEdu.trading.dto.AccountSummaryDto;
import hongik.finEdu.trading.dto.HoldingDto;
import hongik.finEdu.trading.service.TradingAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    @GetMapping
    public ResponseEntity<AccountSummaryDto> getAccount(@RequestParam String userId) {
        return ResponseEntity.ok(tradingAccountService.getAccountSummary(userId));
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingDto>> getHoldings(@RequestParam String userId) {
        return ResponseEntity.ok(tradingAccountService.listHoldings(userId));
    }

    @GetMapping("/holdings/{stockCode}")
    public ResponseEntity<HoldingDto> getHolding(
            @RequestParam String userId,
            @PathVariable String stockCode) {
        return ResponseEntity.ok(tradingAccountService.getHolding(userId, stockCode));
    }
}
