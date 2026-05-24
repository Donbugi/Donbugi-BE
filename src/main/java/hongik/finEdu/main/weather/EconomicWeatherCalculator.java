package hongik.finEdu.main.weather;

import hongik.finEdu.main.weather.client.MarketDataClient;
import hongik.finEdu.main.weather.client.MarketDataParser;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class EconomicWeatherCalculator {

    /**
     * 코스피 등락률(%), S&P500 등락률(%), USD 10일 환율 변동률(%)을 종합해 -6 ~ +6 점수를 산출한다.
     */
    public Result calculate(
            MarketDataClient.KospiSnapshot kospi,
            MarketDataClient.Sp500Snapshot sp500,
            MarketDataClient.ExchangeRateSnapshot exchange) {

        int kospiScore = scoreChangeRate(kospi.fluctuationsRatio());
        BigDecimal sp500ChangeRate = MarketDataParser.computeChangeRate(
                sp500.regularMarketPrice(), sp500.previousClose());
        int sp500Score = scoreChangeRate(sp500ChangeRate);
        int exchangeScore = scoreExchange(exchange.tenDayChangeRate());
        int total = kospiScore + sp500Score + exchangeScore;

        return new Result(
                EconomicWeatherType.fromScore(total),
                total,
                kospiScore,
                sp500Score,
                exchangeScore
        );
    }

    /**
     * 등락률(%) 기준 (-2 ~ +2) — 코스피·S&P500 공통
     */
    static int scoreChangeRate(BigDecimal changeRate) {
        double ratio = changeRate.doubleValue();
        if (ratio >= 1.0) {
            return 2;
        }
        if (ratio >= 0.3) {
            return 1;
        }
        if (ratio > -0.3) {
            return 0;
        }
        if (ratio > -1.0) {
            return -1;
        }
        return -2;
    }

    /**
     * USD/KRW 10일 변동률 기준 (-2 ~ +1)
     */
    static int scoreExchange(BigDecimal tenDayChangeRate) {
        double rate = tenDayChangeRate.doubleValue();
        if (rate < -0.3) {
            return 1;
        }
        if (rate <= 0.5) {
            return 0;
        }
        if (rate < 1.5) {
            return -1;
        }
        return -2;
    }

    public record Result(
            EconomicWeatherType weather,
            int totalScore,
            int kospiScore,
            int sp500Score,
            int exchangeScore
    ) {}
}
