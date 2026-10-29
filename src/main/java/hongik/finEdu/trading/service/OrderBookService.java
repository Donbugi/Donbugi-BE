package hongik.finEdu.trading.service;

import hongik.finEdu.trading.client.NaverOrderBookClient;
import hongik.finEdu.trading.market.OrderBook;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 호가 조회 + 짧은 캐시 + 폴백.
 * 실제 호가를 못 받으면 현재가를 최우선 매도호가로 둔 근사 호가(10단계)를 쓴다 — 체결은 계속되며 메트릭으로 추적.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBookService {

    private final NaverOrderBookClient client;
    private final StockPriceService stockPriceService;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    private final Map<String, OrderBook> cache = new ConcurrentHashMap<>();

    @Value("${mock-trading.orderbook-cache-ms:3000}")
    private long cacheMs;

    @Value("${mock-trading.synthetic-level-quantity:5000}")
    private long syntheticLevelQuantity;

    public OrderBook get(String stockCode) {
        OrderBook cached = cache.get(stockCode);
        if (cached != null && cached.fetchedAt().plusMillis(cacheMs).isAfter(clock.instant())) {
            return cached;
        }
        return fetchFresh(stockCode);
    }

    public OrderBook fetchFresh(String stockCode) {
        Optional<OrderBook> real = client.fetch(stockCode);
        OrderBook book;
        if (real.isPresent()) {
            book = real.get();
            meterRegistry.counter("trading.orderbook.fetch", "source", "naver").increment();
        } else {
            StockPriceService.CachedQuote q = stockPriceService.getCachedQuote(stockCode);
            book = OrderBook.synthetic(stockCode, q.quote().currentPrice(), syntheticLevelQuantity, q.fetchedAt());
            meterRegistry.counter("trading.orderbook.fetch", "source", "synthetic").increment();
        }
        cache.put(stockCode, book);
        return book;
    }
}
