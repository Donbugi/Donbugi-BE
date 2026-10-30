package hongik.finEdu.trading.service;

import hongik.finEdu.trading.client.NaverStockClient;
import hongik.finEdu.trading.client.StockQuote;
import hongik.finEdu.trading.domain.FixedStock;
import hongik.finEdu.trading.domain.TradingConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 종목 시세 캐시 (메모리 L1 → Redis L2 → 네이버).
 *
 * <p>기존 문제: 메모리 캐시에 만료가 없어 Redis TTL 이 무의미했고, 네이버 조회가 실패하면
 * 옛 가격이 영원히 남았으며, 인스턴스마다 다른 가격을 볼 수 있었다.</p>
 * <ul>
 *   <li>L1 은 fetchedAt 기준 TTL(memory-ttl-ms) 적용</li>
 *   <li>Redis 에는 fetchedAt 을 함께 저장 → 체결 엔진이 시세 지연(stale)을 판단</li>
 *   <li>갱신할 때마다 종목별 관측 최저·최고가를 누적 → 지정가 체결 판정의 "구간 저가/고가"로 사용</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final NaverStockClient naverStockClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** L1 캐시: 종목 → (시세, 조회 시각) */
    private final Map<String, CachedQuote> memoryCache = new ConcurrentHashMap<>();
    /** 체결 엔진이 drain 할 때까지 누적되는 종목별 관측 가격 범위 */
    private final Map<String, PriceRange> observedRanges = new ConcurrentHashMap<>();

    @Value("${mock-trading.redis-key-prefix:market:stock:}")
    private String redisKeyPrefix;

    @Value("${mock-trading.price-cache-ttl-seconds:300}")
    private int redisTtlSeconds;

    @Value("${mock-trading.memory-ttl-ms:15000}")
    private long memoryTtlMs;

    public record CachedQuote(StockQuote quote, Instant fetchedAt) {
    }

    public record PriceRange(BigDecimal low, BigDecimal high) {
        PriceRange merge(BigDecimal price) {
            if (price == null || price.signum() <= 0) {
                return this;
            }
            return new PriceRange(low == null ? price : low.min(price), high == null ? price : high.max(price));
        }
    }

    public List<StockQuote> getAllStockQuotes() {
        List<StockQuote> result = new ArrayList<>();
        for (FixedStock stock : TradingConstants.allStocks()) {
            result.add(getQuote(stock.code()));
        }
        return result;
    }

    public StockQuote getQuote(String stockCode) {
        return getCachedQuote(stockCode).quote();
    }

    /** 시세 + 조회 시각. 캐시가 만료됐으면 새로 조회한다. */
    public CachedQuote getCachedQuote(String stockCode) {
        TradingConstants.requireStock(stockCode);
        CachedQuote cached = readCache(stockCode);
        if (cached != null && !isExpired(cached)) {
            return cached;
        }
        return refresh(stockCode);
    }

    public StockQuote getQuoteDetail(String stockCode) {
        TradingConstants.requireStock(stockCode);
        CachedQuote cached = readCache(stockCode);
        if (cached != null && !isExpired(cached) && cached.quote().openPrice() != null) {
            return cached.quote();
        }
        StockQuote live = naverStockClient.fetchQuoteDetail(stockCode);
        return store(stockCode, live).quote();
    }

    public CachedQuote refresh(String stockCode) {
        StockQuote live = naverStockClient.fetchQuote(stockCode);
        return store(stockCode, live);
    }

    public void refreshAllPrices() {
        int success = 0;
        for (FixedStock stock : TradingConstants.allStocks()) {
            try {
                // 시고저가 포함 상세 조회 → 구간 저가/고가 추적에 사용
                store(stock.code(), naverStockClient.fetchQuoteDetail(stock.code()));
                success++;
            } catch (Exception e) {
                log.warn("[모의투자] {} 가격 갱신 실패: {}", stock.code(), e.getMessage());
            }
        }
        log.debug("[모의투자] 주가 갱신 완료: {}/{}", success, TradingConstants.allStocks().size());
    }

    /** 직전 drain 이후 관측된 가격 범위를 반환하고 초기화 (체결 엔진 전용) */
    public PriceRange drainObservedRange(String stockCode) {
        PriceRange r = observedRanges.remove(stockCode);
        return r == null ? new PriceRange(null, null) : r;
    }

    public boolean isStale(CachedQuote q, Duration maxAge) {
        return q.fetchedAt().plus(maxAge).isBefore(clock.instant());
    }

    // ───────────────────────── internal ─────────────────────────

    private boolean isExpired(CachedQuote q) {
        return q.fetchedAt().plusMillis(memoryTtlMs).isBefore(clock.instant());
    }

    private CachedQuote store(String stockCode, StockQuote quote) {
        CachedQuote previous = memoryCache.get(stockCode);
        CachedQuote cq = new CachedQuote(quote, clock.instant());
        memoryCache.put(stockCode, cq);
        trackRange(stockCode, previous == null ? null : previous.quote(), quote);
        try {
            redisTemplate.opsForValue().set(
                    redisKeyPrefix + stockCode, objectMapper.writeValueAsString(cq), Duration.ofSeconds(redisTtlSeconds));
        } catch (Exception e) {
            log.warn("[모의투자] Redis 주가 저장 실패 ({}): {}", stockCode, e.getMessage());
        }
        return cq;
    }

    /**
     * 관측 범위 누적: 현재가 + (당일 저가/고가가 직전 관측보다 갱신됐다면 그 값).
     * 당일 저가가 내려갔다는 건 직전 관측 이후 그 가격에 실제 거래가 있었다는 뜻.
     */
    private void trackRange(String stockCode, StockQuote prev, StockQuote now) {
        observedRanges.compute(stockCode, (k, r) -> {
            PriceRange range = r == null ? new PriceRange(null, null) : r;
            range = range.merge(now.currentPrice());
            if (prev != null) {
                if (now.lowPrice() != null && prev.lowPrice() != null && now.lowPrice().compareTo(prev.lowPrice()) < 0) {
                    range = range.merge(now.lowPrice());
                }
                if (now.highPrice() != null && prev.highPrice() != null && now.highPrice().compareTo(prev.highPrice()) > 0) {
                    range = range.merge(now.highPrice());
                }
            }
            return range;
        });
    }

    private CachedQuote readCache(String stockCode) {
        CachedQuote mem = memoryCache.get(stockCode);
        if (mem != null) {
            return mem;
        }
        try {
            String json = redisTemplate.opsForValue().get(redisKeyPrefix + stockCode);
            if (json == null || json.isBlank()) {
                return null;
            }
            CachedQuote cq = objectMapper.readValue(json, CachedQuote.class);
            if (cq == null || cq.quote() == null || cq.fetchedAt() == null) {
                return null; // 구버전 포맷(StockQuote 단독) 은 무시하고 재조회
            }
            memoryCache.put(stockCode, cq);
            return cq;
        } catch (Exception e) {
            return null;
        }
    }
}
