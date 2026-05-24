package hongik.finEdu.main.weather.client;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class MarketDataClient {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter EXIM_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final String kospiApiUrl;
    private final String sp500NaverApiUrl;
    private final String sp500ApiUrl;
    private final String sp500FallbackStooqUrl;
    private final String exchangeApiBaseUrl;
    private final String koreaeximAuthKey;
    private final String sp500RedisKey;
    private final String sp500StaleRedisKey;
    private final int sp500CacheTtlSeconds;

    public MarketDataClient(
            ObjectMapper objectMapper,
            StringRedisTemplate redisTemplate,
            @Value("${economic-weather.kospi-api-url:https://m.stock.naver.com/api/index/KOSPI/basic}") String kospiApiUrl,
            @Value("${economic-weather.sp500-naver-api-url:https://polling.finance.naver.com/api/realtime/worldstock/stock/SPY}") String sp500NaverApiUrl,
            @Value("${economic-weather.sp500-api-url:https://query2.finance.yahoo.com/v8/finance/chart/%5EGSPC?range=5d&interval=1d}") String sp500ApiUrl,
            @Value("${economic-weather.sp500-fallback-stooq-url:https://stooq.com/q/l/?s=^spx&f=sd2t2ohlcv&h&e=csv}") String sp500FallbackStooqUrl,
            @Value("${economic-weather.exchange-api-base-url:https://oapi.koreaexim.go.kr/site/program/financial/exchangeJSON}") String exchangeApiBaseUrl,
            @Value("${economic-weather.koreaexim-auth-key:}") String koreaeximAuthKey,
            @Value("${economic-weather.sp500-redis-key:market:sp500:now}") String sp500RedisKey,
            @Value("${economic-weather.sp500-stale-redis-key:market:sp500:last}") String sp500StaleRedisKey,
            @Value("${economic-weather.sp500-cache-ttl-seconds:3600}") int sp500CacheTtlSeconds,
            @Value("${economic-weather.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${economic-weather.read-timeout-ms:15000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.kospiApiUrl = kospiApiUrl;
        this.sp500NaverApiUrl = sp500NaverApiUrl;
        this.sp500ApiUrl = sp500ApiUrl;
        this.sp500FallbackStooqUrl = sp500FallbackStooqUrl;
        this.exchangeApiBaseUrl = exchangeApiBaseUrl;
        this.koreaeximAuthKey = koreaeximAuthKey == null ? "" : koreaeximAuthKey.trim();
        this.sp500RedisKey = sp500RedisKey;
        this.sp500StaleRedisKey = sp500StaleRedisKey;
        this.sp500CacheTtlSeconds = sp500CacheTtlSeconds;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public KospiSnapshot fetchKospi() {
        try {
            String body = restClient.get()
                    .uri(kospiApiUrl)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return MarketDataParser.parseKospi(root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[경제날씨] KOSPI 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "KOSPI: " + e.getMessage(), e);
        }
    }

    public Sp500Snapshot fetchSp500() {
        String cached = redisTemplate.opsForValue().get(sp500RedisKey);
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, Sp500Snapshot.class);
            } catch (Exception e) {
                log.warn("[경제날씨] S&P500 캐시 파싱 실패, 재조회");
            }
        }

        try {
            Sp500Snapshot snapshot = fetchSp500Live();
            cacheSp500(snapshot);
            return snapshot;
        } catch (BusinessException e) {
            Sp500Snapshot stale = readStaleSp500();
            if (stale != null) {
                log.warn("[경제날씨] S&P500 조회 실패, 이전 값 사용: {}", e.getMessage());
                return stale;
            }
            throw e;
        } catch (Exception e) {
            log.warn("[경제날씨] S&P500 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500: " + e.getMessage(), e);
        }
    }

    private Sp500Snapshot fetchSp500Live() {
        try {
            return fetchSp500FromNaver();
        } catch (BusinessException naverError) {
            log.warn("[경제날씨] Naver SPY 실패, Yahoo fallback: {}", naverError.getMessage());
        }

        try {
            return fetchSp500FromYahoo(sp500ApiUrl);
        } catch (BusinessException yahooError) {
            log.warn("[경제날씨] Yahoo S&P500 실패, Stooq+Naver fallback: {}", yahooError.getMessage());
            return fetchSp500FromStooqWithNaverRatio();
        }
    }

    private Sp500Snapshot fetchSp500FromNaver() {
        try {
            String body = restClient.get()
                    .uri(sp500NaverApiUrl)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return MarketDataParser.parseSp500FromNaverWorldStock(root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[경제날씨] Naver SPY 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500: " + e.getMessage(), e);
        }
    }

    private Sp500Snapshot fetchSp500FromYahoo(String url) {
        RestClientResponseException last429 = null;
        Exception lastError = null;

        for (String candidateUrl : yahooChartUrls(url)) {
            for (int attempt = 0; attempt < 2; attempt++) {
                if (attempt > 0) {
                    sleepQuietly(1500);
                }
                try {
                    String body = restClient.get()
                            .uri(candidateUrl)
                            .header("Accept", "application/json,text/plain,*/*")
                            .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Referer", "https://finance.yahoo.com/")
                            .retrieve()
                            .body(String.class);
                    JsonNode root = objectMapper.readTree(body);
                    return MarketDataParser.parseSp500(root);
                } catch (BusinessException e) {
                    throw e;
                } catch (RestClientResponseException e) {
                    lastError = e;
                    if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        last429 = e;
                        log.warn("[경제날씨] S&P500 429 (attempt {}): {}", attempt + 1, candidateUrl);
                        continue;
                    }
                    log.warn("[경제날씨] S&P500 조회 실패 ({}): {}", candidateUrl, e.getMessage());
                } catch (Exception e) {
                    lastError = e;
                    log.warn("[경제날씨] S&P500 조회 실패 ({}): {}", candidateUrl, e.getMessage());
                }
            }
        }

        if (last429 != null) {
            throw new BusinessException(
                    ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED,
                    "S&P500: Yahoo Finance 요청 제한(429)",
                    last429);
        }
        throw new BusinessException(
                ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED,
                "S&P500: " + (lastError != null ? lastError.getMessage() : "Yahoo Finance 조회 실패"),
                lastError);
    }

    private static List<String> yahooChartUrls(String primaryUrl) {
        Set<String> urls = new LinkedHashSet<>();
        urls.add(primaryUrl);
        if (primaryUrl.contains("query2.finance.yahoo.com")) {
            urls.add(primaryUrl.replace("query2.finance.yahoo.com", "query1.finance.yahoo.com"));
        } else if (primaryUrl.contains("query1.finance.yahoo.com")) {
            urls.add(primaryUrl.replace("query1.finance.yahoo.com", "query2.finance.yahoo.com"));
        }
        return new ArrayList<>(urls);
    }

    /** Stooq 지수 종가 fallback (Naver·Yahoo 모두 실패 시) */
    private Sp500Snapshot fetchSp500FromStooqWithNaverRatio() {
        try {
            return fetchSp500FromNaver();
        } catch (BusinessException naverError) {
            log.warn("[경제날씨] Stooq fallback 중 Naver 재시도 실패: {}", naverError.getMessage());
        }

        BigDecimal current = fetchStooqClose();
        Sp500Snapshot stale = readStaleSp500();
        if (stale != null) {
            return new Sp500Snapshot(current, stale.previousClose());
        }

        throw new BusinessException(
                ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED,
                "S&P500: Stooq 현재가는 조회됐으나 전일 종가를 가져오지 못했습니다");
    }

    private BigDecimal fetchStooqClose() {
        try {
            String body = restClient.get()
                    .uri(sp500FallbackStooqUrl)
                    .retrieve()
                    .body(String.class);
            return MarketDataParser.parseStooqClose(body);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[경제날씨] Stooq S&P500 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500: " + e.getMessage(), e);
        }
    }

    private Sp500Snapshot readStaleSp500() {
        String stale = redisTemplate.opsForValue().get(sp500StaleRedisKey);
        if (stale == null || stale.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(stale, Sp500Snapshot.class);
        } catch (Exception e) {
            return null;
        }
    }

    private void cacheSp500(Sp500Snapshot snapshot) {
        try {
            String json = objectMapper.writeValueAsString(snapshot);
            redisTemplate.opsForValue().set(sp500RedisKey, json, Duration.ofSeconds(sp500CacheTtlSeconds));
            redisTemplate.opsForValue().set(sp500StaleRedisKey, json);
        } catch (Exception e) {
            log.warn("[경제날씨] S&P500 Redis 저장 실패: {}", e.getMessage());
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public ExchangeRateSnapshot fetchUsdExchangeRate(LocalDate date) {
        if (koreaeximAuthKey.isBlank()) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "KOREAEXIM_AUTH_KEY 미설정");
        }
        for (int daysBack = 0; daysBack < 7; daysBack++) {
            LocalDate target = date.minusDays(daysBack);
            try {
                return fetchUsdExchangeRateForDate(target);
            } catch (BusinessException e) {
                if (daysBack == 6) {
                    throw e;
                }
                log.debug("[경제날씨] {} 환율 없음, 이전일 재시도", target);
            }
        }
        throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "USD 환율 데이터 없음");
    }

    private ExchangeRateSnapshot fetchUsdExchangeRateForDate(LocalDate date) {
        String searchDate = date.format(EXIM_DATE);
        String uri = exchangeApiBaseUrl
                + "?authkey=" + koreaeximAuthKey
                + "&searchdate=" + searchDate
                + "&data=AP01";
        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return MarketDataParser.parseUsdExchange(root);
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.warn("[경제날씨] 환율 조회 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "환율: " + e.getMessage(), e);
        } catch (Exception e) {
            log.warn("[경제날씨] 환율 파싱 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "환율: " + e.getMessage(), e);
        }
    }

    public LocalDate todayInSeoul() {
        return LocalDate.now(SEOUL);
    }

    public record KospiSnapshot(
            BigDecimal closePrice,
            BigDecimal fluctuationsRatio,
            BigDecimal compareToPreviousClosePrice,
            String direction
    ) {}

    public record Sp500Snapshot(
            BigDecimal regularMarketPrice,
            BigDecimal previousClose
    ) {}

    public record ExchangeRateSnapshot(
            BigDecimal usdDealBasRate,
            BigDecimal tenDayChangeRate
    ) {}
}
