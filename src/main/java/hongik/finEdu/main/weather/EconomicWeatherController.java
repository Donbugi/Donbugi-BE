package hongik.finEdu.main.weather;

import hongik.finEdu.main.weather.dto.EconomicWeatherResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/main")
@RequiredArgsConstructor
public class EconomicWeatherController {

    private final EconomicWeatherService economicWeatherService;

    /**
     * 경제 날씨 (KOSPI + S&P500 + USD 환율 종합)
     * GET /api/main/economic-weather
     */
    @GetMapping("/economic-weather")
    public ResponseEntity<EconomicWeatherResponseDto> getEconomicWeather() {
        return ResponseEntity.ok(economicWeatherService.getTodayWeather());
    }
}
