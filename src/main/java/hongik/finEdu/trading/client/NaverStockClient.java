package hongik.finEdu.trading.client;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
public class NaverStockClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String basicApiUrlTemplate;
    private final String pollingApiUrlTemplate;

    public NaverStockClient(
            ObjectMapper objectMapper,
            @Value("${mock-trading.naver-stock-api-url:https://m.stock.naver.com/api/stock/{code}/basic}") String basicApiUrlTemplate,
            @Value("${mock-trading.naver-polling-api-url:https://polling.finance.naver.com/api/realtime/domestic/stock/{code}}") String pollingApiUrlTemplate,
            @Value("${mock-trading.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${mock-trading.read-timeout-ms:15000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.basicApiUrlTemplate = basicApiUrlTemplate;
        this.pollingApiUrlTemplate = pollingApiUrlTemplate;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .build();
    }

    public StockQuote fetchQuote(String stockCode) {
        try {
            return fetchFromUrl(basicApiUrlTemplate.replace("{code}", stockCode), stockCode);
        } catch (BusinessException basicError) {
            log.debug("[모의투자] basic API 실패 ({}), polling fallback: {}", stockCode, basicError.getMessage());
            return fetchFromUrl(pollingApiUrlTemplate.replace("{code}", stockCode), stockCode);
        }
    }

    /** 상세 조회 — polling 우선 (시고저·거래량 포함) */
    public StockQuote fetchQuoteDetail(String stockCode) {
        try {
            StockQuote polling = fetchFromUrl(pollingApiUrlTemplate.replace("{code}", stockCode), stockCode);
            if (polling.openPrice() != null) {
                return polling;
            }
        } catch (BusinessException ignored) {
            // fallback to basic
        }
        return fetchFromUrl(basicApiUrlTemplate.replace("{code}", stockCode), stockCode);
    }

    private StockQuote fetchFromUrl(String url, String stockCode) {
        try {
            String body = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            return NaverStockParser.parse(stockCode, root);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[모의투자] {} 시세 조회 실패: {}", stockCode, e.getMessage());
            throw new BusinessException(ErrorCode.STOCK_PRICE_FETCH_FAILED, stockCode + ": " + e.getMessage(), e);
        }
    }
}
