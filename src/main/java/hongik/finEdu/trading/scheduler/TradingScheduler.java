package hongik.finEdu.trading.scheduler;

import hongik.finEdu.trading.service.MarketHoursService;
import hongik.finEdu.trading.service.PendingOrderCheckService;
import hongik.finEdu.trading.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TradingScheduler {

    private final StockPriceService stockPriceService;
    private final PendingOrderCheckService pendingOrderCheckService;
    private final MarketHoursService marketHoursService;

    /** 5분마다 20종목 주가 Redis 갱신 */
    @Scheduled(fixedDelayString = "${mock-trading.price-refresh-ms:300000}")
    public void refreshStockPrices() {
        stockPriceService.refreshAllPrices();
    }

    /** 5분마다 지정가 체결 확인 (장중만) */
    @Scheduled(fixedDelayString = "${mock-trading.pending-check-ms:300000}")
    public void checkPendingOrders() {
        if (marketHoursService.isMarketOpen()) {
            pendingOrderCheckService.checkAndFillPendingOrders();
        }
    }

    /** 평일 15:30 장 마감 — 미체결 지정가 당일 취소 */
    @Scheduled(cron = "${mock-trading.market-close-cron:0 30 15 * * MON-FRI}", zone = "Asia/Seoul")
    public void closeMarket() {
        log.info("[모의투자] 장 마감 — 미체결 주문 취소");
        pendingOrderCheckService.cancelAllPendingOrdersAtMarketClose();
    }
}
