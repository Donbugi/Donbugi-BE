package hongik.finEdu.main.weather.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EconomicWeatherHistoryDto(
        LocalDate date,
        String weatherCode,
        String weatherLabel,
        String emoji,
        int score,
        BigDecimal kospiChangeRate,
        BigDecimal sp500ChangeRate,
        BigDecimal fxTenDayChangeRate
) {
}
