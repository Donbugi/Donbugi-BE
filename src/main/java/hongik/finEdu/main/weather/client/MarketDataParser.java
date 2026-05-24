package hongik.finEdu.main.weather.client;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 외부 API JSON 응답을 파싱한다. 모든 시세·등락률은 여기서만 추출한다.
 */
public final class MarketDataParser {

    private MarketDataParser() {}

    public static MarketDataClient.KospiSnapshot parseKospi(JsonNode root) {
        BigDecimal closePrice = parseDecimal(root.path("closePrice").asText());
        BigDecimal fluctuationsRatio = parseDecimal(root.path("fluctuationsRatio").asText());
        BigDecimal compareToPrevious = parseDecimal(root.path("compareToPreviousClosePrice").asText());
        String direction = root.path("compareToPreviousPrice").path("name").asText("");
        return new MarketDataClient.KospiSnapshot(closePrice, fluctuationsRatio, compareToPrevious, direction);
    }

    /** Naver 해외주식 SPY (S&P500 ETF) — Yahoo 429 회피용 1순위 소스 */
    public static MarketDataClient.Sp500Snapshot parseSp500FromNaverWorldStock(JsonNode root) {
        JsonNode data = root.path("datas").path(0);
        if (data.isMissingNode()) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500 Naver 응답 없음");
        }
        BigDecimal closePrice = parseDecimal(data.path("closePrice").asText());
        BigDecimal fluctuationsRatio = parseDecimal(data.path("fluctuationsRatio").asText());
        BigDecimal previousClose = computePreviousClose(closePrice, fluctuationsRatio);
        return new MarketDataClient.Sp500Snapshot(closePrice, previousClose);
    }

    public static MarketDataClient.Sp500Snapshot parseSp500(JsonNode root) {
        MarketDataClient.Sp500Snapshot fromChart = parseSp500FromCloseSeries(root);
        if (fromChart != null) {
            return fromChart;
        }

        JsonNode meta = root.path("chart").path("result").path(0).path("meta");
        if (meta.isMissingNode()) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500 meta 없음");
        }
        BigDecimal regularMarketPrice = BigDecimal.valueOf(meta.path("regularMarketPrice").asDouble());
        BigDecimal previousClose = readPreviousClose(meta);
        if (previousClose.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500 previousClose=0");
        }
        return new MarketDataClient.Sp500Snapshot(regularMarketPrice, previousClose);
    }

    /** chart.indicators.quote[0].close 배열에서 최근 2거래일 종가 사용 */
    public static MarketDataClient.Sp500Snapshot parseSp500FromCloseSeries(JsonNode root) {
        JsonNode closes = root.path("chart").path("result").path(0)
                .path("indicators").path("quote").path(0).path("close");
        if (!closes.isArray()) {
            return null;
        }

        BigDecimal previousClose = null;
        BigDecimal regularMarketPrice = null;
        for (JsonNode node : closes) {
            if (node == null || node.isNull()) {
                continue;
            }
            previousClose = regularMarketPrice;
            regularMarketPrice = BigDecimal.valueOf(node.asDouble());
        }
        if (regularMarketPrice == null || previousClose == null) {
            return null;
        }
        return new MarketDataClient.Sp500Snapshot(regularMarketPrice, previousClose);
    }

    public static BigDecimal parseStooqClose(String csv) {
        if (csv == null || csv.isBlank()) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500 Stooq 응답 없음");
        }
        String[] lines = csv.trim().split("\\R");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("Symbol,")) {
                continue;
            }
            String[] parts = line.split(",");
            if (parts.length >= 7 && !"N/D".equals(parts[6])) {
                return parseDecimal(parts[6]);
            }
        }
        throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500 Stooq CSV 파싱 실패");
    }

    private static BigDecimal readPreviousClose(JsonNode meta) {
        if (meta.hasNonNull("previousClose") && meta.path("previousClose").asDouble() != 0) {
            return BigDecimal.valueOf(meta.path("previousClose").asDouble());
        }
        if (meta.hasNonNull("chartPreviousClose") && meta.path("chartPreviousClose").asDouble() != 0) {
            return BigDecimal.valueOf(meta.path("chartPreviousClose").asDouble());
        }
        throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "S&P500 previousClose 없음");
    }

    public static MarketDataClient.ExchangeRateSnapshot parseUsdExchange(JsonNode root) {
        if (!root.isArray() || root.isEmpty()) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "환율 API 응답 없음");
        }
        JsonNode usd = null;
        for (JsonNode item : root) {
            if ("USD".equals(item.path("cur_unit").asText())) {
                usd = item;
                break;
            }
        }
        if (usd == null) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "USD 환율 데이터 없음");
        }
        BigDecimal dealBasRate = parseDecimal(usd.path("deal_bas_r").asText());
        BigDecimal tenDayChangeRate = parseDecimal(usd.path("ten_dd_efee_r").asText("0"));
        return new MarketDataClient.ExchangeRateSnapshot(dealBasRate, tenDayChangeRate);
    }

    /** S&P500 등락률(%) = (regularMarketPrice - previousClose) / previousClose * 100 */
    public static BigDecimal computeChangeRate(BigDecimal current, BigDecimal previousClose) {
        return current.subtract(previousClose)
                .divide(previousClose, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    /** 등락률(%)로 전일 종가 역산 */
    public static BigDecimal computePreviousClose(BigDecimal current, BigDecimal fluctuationsRatioPercent) {
        return current.divide(
                BigDecimal.ONE.add(fluctuationsRatioPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)),
                4,
                RoundingMode.HALF_UP);
    }

    static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "숫자 파싱 실패: 빈 값");
        }
        try {
            return new BigDecimal(raw.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.ECONOMIC_WEATHER_FETCH_FAILED, "숫자 파싱 실패: " + raw);
        }
    }
}
