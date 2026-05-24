package hongik.finEdu.main.weather;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EconomicWeatherType {

    SUNNY("SUNNY", "맑음", "☀️", "시장 분위기가 밝습니다. 투자 심리가 안정적이에요."),
    PARTLY_CLOUDY("PARTLY_CLOUDY", "구름 조금", "⛅", "전반적으로 양호하지만, 일부 불확실성이 남아 있어요."),
    CLOUDY("CLOUDY", "흐림", "☁️", "시장이 관망세입니다. 큰 움직임 없이 조심스러운 흐름이에요."),
    FOGGY("FOGGY", "안개", "🌫️", "방향성이 불분명합니다. 변동성·환율 불안으로 시야가 흐려졌어요."),
    RAINY("RAINY", "비", "🌧️", "시장에 부정적 기운이 감돕니다. 리스크 관리에 유의하세요."),
    STORM("STORM", "폭풍", "⛈️", "글로벌·국내 증시 급락 등 극단적 상황입니다. 매우 신중하게 대응하세요.");

    private final String code;
    private final String label;
    private final String emoji;
    private final String description;

    public static EconomicWeatherType fromScore(int score) {
        if (score >= 4) {
            return SUNNY;
        }
        if (score >= 2) {
            return PARTLY_CLOUDY;
        }
        if (score >= 0) {
            return CLOUDY;
        }
        if (score >= -2) {
            return FOGGY;
        }
        if (score >= -4) {
            return RAINY;
        }
        return STORM;
    }
}
