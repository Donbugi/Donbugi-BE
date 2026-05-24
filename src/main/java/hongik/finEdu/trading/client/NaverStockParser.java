package hongik.finEdu.trading.client;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;

public final class NaverStockParser {

    private NaverStockParser() {}

    public static StockQuote parse(String stockCode, JsonNode root) {
        if (root == null || root.isMissingNode()) {
            throw new BusinessException(ErrorCode.STOCK_PRICE_FETCH_FAILED, stockCode + " 응답 없음");
        }

        JsonNode data = root.has("datas") ? root.path("datas").path(0) : root;
        if (data.isMissingNode()) {
            throw new BusinessException(ErrorCode.STOCK_PRICE_FETCH_FAILED, stockCode + " 데이터 없음");
        }

        String name = text(data, "stockName");
        BigDecimal close = parseDecimal(text(data, "closePrice"));
        BigDecimal changeRate = parseDecimal(text(data, "fluctuationsRatio"));
        BigDecimal changePrice = parseDecimal(text(data, "compareToPreviousClosePrice"));
        String direction = data.path("compareToPreviousPrice").path("name").asString("");

        BigDecimal open = parseOptionalDecimal(text(data, "openPrice"));
        BigDecimal high = parseOptionalDecimal(text(data, "highPrice"));
        BigDecimal low = parseOptionalDecimal(text(data, "lowPrice"));
        Long volume = parseOptionalLong(text(data, "accumulatedTradingVolumeRaw",
                text(data, "accumulatedTradingVolume")));
        String marketStatus = text(data, "marketStatus");

        return new StockQuote(
                stockCode,
                name.isBlank() ? stockCode : name,
                close,
                changeRate,
                changePrice,
                direction,
                open,
                high,
                low,
                volume,
                marketStatus
        );
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field)) {
            return "";
        }
        String s = node.path(field).asString();
        return s != null ? s.trim() : "";
    }

    private static String text(JsonNode node, String primary, String fallback) {
        String v = text(node, primary);
        return v.isBlank() ? fallback : v;
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(raw.replace(",", "").replace("+", "").trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.STOCK_PRICE_FETCH_FAILED, "숫자 파싱 실패: " + raw);
        }
    }

    private static BigDecimal parseOptionalDecimal(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.replace(",", "").replace("+", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long parseOptionalLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
