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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final NaverStockClient naverStockClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, StockQuote> memoryCache = new ConcurrentHashMap<>();

    @Value("${mock-trading.redis-key-prefix:market:stock:}")
    private String redisKeyPrefix;

    @Value("${mock-trading.price-cache-ttl-seconds:300}")
    private int cacheTtlSeconds;

    public List<StockQuote> getAllStockQuotes() {
        List<StockQuote> result = new ArrayList<>();
        for (FixedStock stock : TradingConstants.allStocks()) {
            result.add(getQuote(stock.code()));
        }
        return result;
    }

    public StockQuote getQuote(String stockCode) {
        TradingConstants.requireStock(stockCode);
        StockQuote cached = readCache(stockCode);
        if (cached != null) {
            return cached;
        }
        StockQuote live = naverStockClient.fetchQuote(stockCode);
        writeCache(stockCode, live);
        return live;
    }

    public StockQuote getQuoteDetail(String stockCode) {
        TradingConstants.requireStock(stockCode);
        StockQuote cached = readCache(stockCode);
        if (cached != null && cached.openPrice() != null) {
            return cached;
        }
        StockQuote live = naverStockClient.fetchQuoteDetail(stockCode);
        writeCache(stockCode, live);
        return live;
    }

    public void refreshAllPrices() {
        int success = 0;
        for (FixedStock stock : TradingConstants.allStocks()) {
            try {
                StockQuote quote = naverStockClient.fetchQuote(stock.code());
                writeCache(stock.code(), quote);
                success++;
            } catch (Exception e) {
                log.warn("[모의투자] {} 가격 갱신 실패: {}", stock.code(), e.getMessage());
            }
        }
        log.info("[모의투자] 주가 갱신 완료: {}/{}", success, TradingConstants.allStocks().size());
    }

    private StockQuote readCache(String stockCode) {
        StockQuote mem = memoryCache.get(stockCode);
        if (mem != null) {
            return mem;
        }
        try {
            String json = redisTemplate.opsForValue().get(redisKeyPrefix + stockCode);
            if (json == null || json.isBlank()) {
                return null;
            }
            StockQuote quote = objectMapper.readValue(json, StockQuote.class);
            memoryCache.put(stockCode, quote);
            return quote;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(String stockCode, StockQuote quote) {
        memoryCache.put(stockCode, quote);
        try {
            String json = objectMapper.writeValueAsString(quote);
            redisTemplate.opsForValue().set(
                    redisKeyPrefix + stockCode, json, Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.warn("[모의투자] Redis 주가 저장 실패 ({}): {}", stockCode, e.getMessage());
        }
    }
}
