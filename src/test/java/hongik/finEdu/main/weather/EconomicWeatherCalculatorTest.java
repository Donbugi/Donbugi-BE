package hongik.finEdu.main.weather;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class EconomicWeatherCalculatorTest {

    @Test
    void scoreChangeRate_thresholds() {
        assertThat(EconomicWeatherCalculator.scoreChangeRate(bd("1.2"))).isEqualTo(2);
        assertThat(EconomicWeatherCalculator.scoreChangeRate(bd("0.5"))).isEqualTo(1);
        assertThat(EconomicWeatherCalculator.scoreChangeRate(bd("0.0"))).isEqualTo(0);
        assertThat(EconomicWeatherCalculator.scoreChangeRate(bd("-0.5"))).isEqualTo(-1);
        assertThat(EconomicWeatherCalculator.scoreChangeRate(bd("-1.5"))).isEqualTo(-2);
    }

    @Test
    void scoreExchange_thresholds() {
        assertThat(EconomicWeatherCalculator.scoreExchange(bd("-0.5"))).isEqualTo(1);
        assertThat(EconomicWeatherCalculator.scoreExchange(bd("0.3"))).isEqualTo(0);
        assertThat(EconomicWeatherCalculator.scoreExchange(bd("1.0"))).isEqualTo(-1);
        assertThat(EconomicWeatherCalculator.scoreExchange(bd("2.0"))).isEqualTo(-2);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
