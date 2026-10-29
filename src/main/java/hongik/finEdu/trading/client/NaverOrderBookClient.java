package hongik.finEdu.trading.client;

import hongik.finEdu.trading.market.OrderBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 네이버 증권 10단계 호가 조회.
 *
 * <p>비공식 API 라 응답 스키마가 바뀔 수 있으므로 필드명을 고정하지 않고
 * "가격 + 잔량" 쌍을 가진 배열을 찾아 매도(sell/ask/offer)·매수(buy/bid) 로 분류하는 방어적 파서를 쓴다.
 * 파싱에 실패하면 empty 를 반환하고, 호출 측은 현재가 기반 근사 호가로 폴백한다.</p>
 */
@Slf4j
@Component
public class NaverOrderBookClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36";
    private static final List<String> PRICE_KEYS = List.of("price", "hogaprice", "sellprice", "buyprice", "askprice", "bidprice");
    private static final List<String> QTY_KEYS = List.of("count", "quantity", "remainquantity", "volume", "amount",
            "sellcount", "buycount", "askquantity", "bidquantity", "remain");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String urlTemplate;
    private final boolean enabled;

    public NaverOrderBookClient(
            ObjectMapper objectMapper,
            @Value("${mock-trading.orderbook-api-url:https://stock.naver.com/api/domestic/detail/{code}/hoga}") String urlTemplate,
            @Value("${mock-trading.orderbook-enabled:true}") boolean enabled,
            @Value("${mock-trading.connect-timeout-ms:10000}") int connectTimeoutMs,
            @Value("${mock-trading.read-timeout-ms:15000}") int readTimeoutMs) {
        this.objectMapper = objectMapper;
        this.urlTemplate = urlTemplate;
        this.enabled = enabled;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(connectTimeoutMs, 3000));
        factory.setReadTimeout(Math.min(readTimeoutMs, 5000));
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Referer", "https://stock.naver.com/")
                .build();
    }

    public Optional<OrderBook> fetch(String stockCode) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String body = restClient.get().uri(urlTemplate.replace("{code}", stockCode)).retrieve().body(String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            return parse(stockCode, objectMapper.readTree(body), Instant.now());
        } catch (Exception e) {
            log.debug("[호가] {} 조회 실패: {}", stockCode, e.getMessage());
            return Optional.empty();
        }
    }

    /** 테스트 가능하도록 static 파서 분리 */
    public static Optional<OrderBook> parse(String stockCode, JsonNode root, Instant at) {
        List<OrderBook.Level> asks = new ArrayList<>();
        List<OrderBook.Level> bids = new ArrayList<>();
        collect(root, "", asks, bids, 0);
        if (asks.isEmpty() || bids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new OrderBook(stockCode, asks, bids, at, false));
    }

    private static void collect(JsonNode node, String path, List<OrderBook.Level> asks, List<OrderBook.Level> bids, int depth) {
        if (node == null || depth > 6) {
            return;
        }
        if (node.isArray()) {
            Side side = sideOf(path);
            for (JsonNode el : node) {
                if (el.isObject() && side != Side.UNKNOWN) {
                    level(el, path).ifPresent(l -> (side == Side.ASK ? asks : bids).add(l));
                } else {
                    collect(el, path, asks, bids, depth + 1);
                }
            }
            return;
        }
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                collect(node.get(name), path + "/" + name, asks, bids, depth + 1);
            }
        }
    }

    private enum Side { ASK, BID, UNKNOWN }

    private static Side sideOf(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        String last = p.substring(p.lastIndexOf('/') + 1);
        if (last.contains("sell") || last.contains("ask") || last.contains("offer") || last.contains("매도")) {
            return Side.ASK;
        }
        if (last.contains("buy") || last.contains("bid") || last.contains("매수")) {
            return Side.BID;
        }
        return Side.UNKNOWN;
    }

    private static Optional<OrderBook.Level> level(JsonNode el, String path) {
        BigDecimal price = null;
        Long qty = null;
        for (String name : el.propertyNames()) {
            String k = name.toLowerCase(Locale.ROOT);
            String v = el.get(name).asString("").replace(",", "").replace("+", "").trim();
            if (v.isEmpty()) {
                continue;
            }
            try {
                if (price == null && PRICE_KEYS.contains(k)) {
                    price = new BigDecimal(v);
                } else if (qty == null && QTY_KEYS.contains(k)) {
                    qty = new BigDecimal(v).longValue();
                }
            } catch (NumberFormatException ignored) {
                // 숫자가 아닌 필드
            }
        }
        if (price == null || qty == null || price.signum() <= 0 || qty <= 0) {
            return Optional.empty();
        }
        return Optional.of(new OrderBook.Level(price, qty));
    }
}
