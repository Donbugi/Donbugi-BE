package hongik.finEdu.main.weather.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EconomicWeatherResponseDto {

    private String weatherCode;
    private String weatherLabel;
    private String emoji;
    private String description;
    private int score;
    private LocalDate date;
    private KospiIndicatorDto kospi;
    private Sp500IndicatorDto sp500;
    private ExchangeRateIndicatorDto exchangeRate;
    private boolean cached;
    private Instant at;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KospiIndicatorDto {
        private BigDecimal closePrice;
        private BigDecimal fluctuationsRatio;
        private BigDecimal compareToPreviousClosePrice;
        private String direction;
        private int sentimentScore;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sp500IndicatorDto {
        private BigDecimal regularMarketPrice;
        private BigDecimal previousClose;
        private BigDecimal changeRate;
        private int sentimentScore;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExchangeRateIndicatorDto {
        private BigDecimal usdDealBasRate;
        private BigDecimal tenDayChangeRate;
        private int sentimentScore;
    }
}
