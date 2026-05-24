package hongik.finEdu.main.weather;

import hongik.finEdu.main.weather.client.MarketDataClient;
import hongik.finEdu.main.weather.client.MarketDataParser;
import hongik.finEdu.main.weather.dto.EconomicWeatherResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class EconomicWeatherService {

    private final MarketDataClient marketDataClient;
    private final EconomicWeatherCalculator calculator;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${economic-weather.redis-key-prefix:market:economic-weather:}")
    private String redisKeyPrefix;

    @Value("${economic-weather.cache-ttl-seconds:1800}")
    private int cacheTtlSeconds;

    public EconomicWeatherResponseDto getTodayWeather() {
        LocalDate today = marketDataClient.todayInSeoul();
        String cacheKey = redisKeyPrefix + today;

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            try {
                EconomicWeatherResponseDto dto = objectMapper.readValue(cached, EconomicWeatherResponseDto.class);
                return EconomicWeatherResponseDto.builder()
                        .weatherCode(dto.getWeatherCode())
                        .weatherLabel(dto.getWeatherLabel())
                        .emoji(dto.getEmoji())
                        .description(dto.getDescription())
                        .score(dto.getScore())
                        .date(dto.getDate())
                        .kospi(dto.getKospi())
                        .sp500(dto.getSp500())
                        .exchangeRate(dto.getExchangeRate())
                        .cached(true)
                        .at(Instant.now())
                        .build();
            } catch (Exception e) {
                log.warn("[경제날씨] 캐시 파싱 실패, 재조회: {}", e.getMessage());
            }
        }

        EconomicWeatherResponseDto fresh = fetchAndCalculate(today);
        try {
            String json = objectMapper.writeValueAsString(fresh);
            redisTemplate.opsForValue().set(cacheKey, json, Duration.ofSeconds(cacheTtlSeconds));
            log.info("[경제날씨] {} → {} (score={}), ttl={}s",
                    today, fresh.getWeatherLabel(), fresh.getScore(), cacheTtlSeconds);
        } catch (Exception e) {
            log.warn("[경제날씨] Redis 저장 실패: {}", e.getMessage());
        }

        return fresh;
    }

    private EconomicWeatherResponseDto fetchAndCalculate(LocalDate today) {
        MarketDataClient.KospiSnapshot kospi = marketDataClient.fetchKospi();
        MarketDataClient.Sp500Snapshot sp500 = marketDataClient.fetchSp500();
        MarketDataClient.ExchangeRateSnapshot exchange = marketDataClient.fetchUsdExchangeRate(today);

        EconomicWeatherCalculator.Result result = calculator.calculate(kospi, sp500, exchange);
        EconomicWeatherType weather = result.weather();
        var sp500ChangeRate = MarketDataParser.computeChangeRate(
                sp500.regularMarketPrice(), sp500.previousClose());

        return EconomicWeatherResponseDto.builder()
                .weatherCode(weather.getCode())
                .weatherLabel(weather.getLabel())
                .emoji(weather.getEmoji())
                .description(weather.getDescription())
                .score(result.totalScore())
                .date(today)
                .kospi(EconomicWeatherResponseDto.KospiIndicatorDto.builder()
                        .closePrice(kospi.closePrice())
                        .fluctuationsRatio(kospi.fluctuationsRatio())
                        .compareToPreviousClosePrice(kospi.compareToPreviousClosePrice())
                        .direction(kospi.direction())
                        .sentimentScore(result.kospiScore())
                        .build())
                .sp500(EconomicWeatherResponseDto.Sp500IndicatorDto.builder()
                        .regularMarketPrice(sp500.regularMarketPrice())
                        .previousClose(sp500.previousClose())
                        .changeRate(sp500ChangeRate)
                        .sentimentScore(result.sp500Score())
                        .build())
                .exchangeRate(EconomicWeatherResponseDto.ExchangeRateIndicatorDto.builder()
                        .usdDealBasRate(exchange.usdDealBasRate())
                        .tenDayChangeRate(exchange.tenDayChangeRate())
                        .sentimentScore(result.exchangeScore())
                        .build())
                .cached(false)
                .at(Instant.now())
                .build();
    }
}
