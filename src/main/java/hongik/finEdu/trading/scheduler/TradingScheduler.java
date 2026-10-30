package hongik.finEdu.trading.scheduler;

import hongik.finEdu.common.lock.DistributedLock;
import hongik.finEdu.trading.engine.MatchingEngine;
import hongik.finEdu.trading.engine.TradingReportJob;
import hongik.finEdu.trading.market.MarketSession;
import hongik.finEdu.trading.service.MarketClock;
import hongik.finEdu.trading.service.StockPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 모의투자 스케줄러. 모든 작업은 분산 락으로 단일 인스턴스에서만 실행.
 *
 * <pre>
 *  08:30~15:30  시세 갱신 20초 (그 외 5분)
 *  09:00~15:20  지정가 매칭 20초
 *  09:00:20     장 시작 동시호가 단일가(시가) 체결
 *  15:30:20     장 마감 동시호가 단일가(종가) 체결
 *  15:31        미체결 잔량 취소
 *  15:40        일별 자산 스냅샷 + 랭킹 + 정합성 검증
 * </pre>
 * 기존: 5분 주기 폴링 → 시장가도 최대 5분 묵은 가격으로 체결, 휴장일에도 동작.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TradingScheduler {

    private static final String ZONE = "Asia/Seoul";

    private final StockPriceService stockPriceService;
    private final MatchingEngine matchingEngine;
    private final TradingReportJob reportJob;
    private final MarketClock marketClock;
    private final DistributedLock lock;

    private volatile long lastIdleRefreshMs = 0;

    @Scheduled(fixedDelayString = "${mock-trading.price-refresh-ms:20000}")
    public void refreshStockPrices() {
        MarketSession s = marketClock.session();
        long now = System.currentTimeMillis();
        if (s == MarketSession.CLOSED) {
            // 장외에는 5분마다만 갱신 (화면 표시용)
            if (now - lastIdleRefreshMs < 300_000) {
                return;
            }
            lastIdleRefreshMs = now;
        }
        lock.runExclusively("sched:price-refresh", Duration.ofSeconds(60), stockPriceService::refreshAllPrices);
    }

    @Scheduled(fixedDelayString = "${mock-trading.pending-check-ms:20000}", initialDelay = 10_000)
    public void matchPendingOrders() {
        if (marketClock.session() != MarketSession.REGULAR) {
            return;
        }
        lock.runExclusively("sched:matching", Duration.ofSeconds(60), matchingEngine::matchContinuous);
    }

    @Scheduled(cron = "20 0 9 * * MON-FRI", zone = ZONE)
    public void openingAuction() {
        if (!marketClock.isTradingDay()) {
            return;
        }
        lock.runExclusively("sched:auction-open", Duration.ofMinutes(5), () -> matchingEngine.runAuction(true));
    }

    @Scheduled(cron = "20 30 15 * * MON-FRI", zone = ZONE)
    public void closingAuction() {
        if (!marketClock.isTradingDay()) {
            return;
        }
        lock.runExclusively("sched:auction-close", Duration.ofMinutes(5), () -> matchingEngine.runAuction(false));
    }

    @Scheduled(cron = "0 31 15 * * MON-FRI", zone = ZONE)
    public void closeMarket() {
        lock.runExclusively("sched:market-close", Duration.ofMinutes(5), matchingEngine::expireAll);
    }

    @Scheduled(cron = "0 40 15 * * MON-FRI", zone = ZONE)
    public void endOfDayReports() {
        if (!marketClock.isTradingDay()) {
            return;
        }
        lock.runExclusively("sched:eod-report", Duration.ofMinutes(20), reportJob::runEndOfDay);
    }
}
