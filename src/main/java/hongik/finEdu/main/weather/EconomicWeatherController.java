package hongik.finEdu.main.weather;

import hongik.finEdu.config.OpenApiTags;
import hongik.finEdu.main.weather.dto.EconomicWeatherResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = OpenApiTags.MAIN)
@RestController
@RequestMapping("/api/main")
@RequiredArgsConstructor
public class EconomicWeatherController {

    private final EconomicWeatherService economicWeatherService;

    @Operation(
            summary = "경제 날씨 (시장 심리)",
            description = """
                    KOSPI + S&P500 + USD 환율을 종합한 '시장 날씨'.
                    - weatherCode/Label/emoji: 맑음~폭풍
                    - detailReasonLines: FE 팝업용 bullet 근거
                    - 일별 Redis 캐시. 인증 불필요.""")
    @GetMapping("/economic-weather")
    public ResponseEntity<EconomicWeatherResponseDto> getEconomicWeather() {
        return ResponseEntity.ok(economicWeatherService.getTodayWeather());
    }
}
