package hongik.finEdu.trading.client;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class NaverOrderBookParserTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void 매도_매수_배열을_필드명으로_분류해_파싱() throws Exception {
        String json = """
                {"sellInfos":[{"price":"70,100","count":"1,200"},{"price":"70,000","count":"300"}],
                 "buyInfos":[{"price":"69,900","count":"500"}],
                 "totalSell":"1500"}
                """;
        var book = NaverOrderBookClient.parse("005930", om.readTree(json), Instant.now()).orElseThrow();
        assertThat(book.bestAsk()).isEqualByComparingTo("70000");
        assertThat(book.bestBid()).isEqualByComparingTo("69900");
        assertThat(book.asks().get(1).quantity()).isEqualTo(1200);
        assertThat(book.synthetic()).isFalse();
    }

    @Test
    void 중첩_구조도_탐색() throws Exception {
        String json = """
                {"result":{"askList":[{"askPrice":"1000","askQuantity":"10"}],"bidList":[{"bidPrice":"999","bidQuantity":"5"}]}}
                """;
        assertThat(NaverOrderBookClient.parse("x", om.readTree(json), Instant.now())).isPresent();
    }

    @Test
    void 알_수_없는_스키마면_empty() throws Exception {
        assertThat(NaverOrderBookClient.parse("x", om.readTree("{\"foo\":[1,2,3]}"), Instant.now())).isEmpty();
    }
}
