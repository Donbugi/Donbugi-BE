package hongik.finEdu.trading.market;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketRulesTest {

    static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    @Nested
    class 호가단위 {
        @ParameterizedTest
        @CsvSource({"1999,1", "2000,5", "4995,5", "5000,10", "19990,10", "20000,50", "55000,100",
                "199900,100", "200000,500", "499500,500", "500000,1000"})
        void 가격대별_호가단위(String price, String tick) {
            assertThat(TickSizePolicy.tickSize(d(price))).isEqualByComparingTo(tick);
        }

        @Test
        void 단위에_맞지_않으면_무효() {
            assertThat(TickSizePolicy.isValidTick(d("55100"))).isTrue();
            assertThat(TickSizePolicy.isValidTick(d("55100.00"))).isTrue();
            assertThat(TickSizePolicy.isValidTick(d("55150"))).isFalse();
            assertThat(TickSizePolicy.isValidTick(d("55100.5"))).isFalse();
        }

        @Test
        void 구간_경계의_한_틱_위아래() {
            assertThat(TickSizePolicy.down(d("2000"), 1)).isEqualByComparingTo("1999");
            assertThat(TickSizePolicy.down(d("2005"), 1)).isEqualByComparingTo("2000");
            assertThat(TickSizePolicy.up(d("1999"), 1)).isEqualByComparingTo("2000");
            assertThat(TickSizePolicy.up(d("2000"), 1)).isEqualByComparingTo("2005");
        }
    }

    @Nested
    class 가격제한폭 {
        @Test
        void 상한가는_절사_하한가는_절상() {
            var band = PriceLimitPolicy.band(d("70000"));
            assertThat(band.upper()).isEqualByComparingTo("91000");
            assertThat(band.lower()).isEqualByComparingTo("49000");
            assertThat(PriceLimitPolicy.band(d("1540")).upper()).isEqualByComparingTo("2000"); // 2002 → 2000
            assertThat(PriceLimitPolicy.band(d("2860")).lower()).isEqualByComparingTo("2005"); // 2002 → 2005
        }
    }

    @Nested
    class 수수료_세금 {
        final FeeTaxPolicy policy = new FeeTaxPolicy(null);

        @Test
        void 수수료는_원_미만_절사() {
            assertThat(policy.fee(d("1000000"))).isEqualByComparingTo("150");
            assertThat(policy.fee(d("999999"))).isEqualByComparingTo("149");
        }

        @Test
        void 거래세는_시행일별_세율() {
            assertThat(policy.sellTax(d("1000000"), MarketType.KOSPI, LocalDate.of(2026, 10, 29))).isEqualByComparingTo("2000");
            assertThat(policy.sellTax(d("1000000"), MarketType.KOSPI, LocalDate.of(2025, 6, 1))).isEqualByComparingTo("1500");
            assertThat(policy.sellTax(d("1000000"), MarketType.KOSDAQ, LocalDate.of(2024, 6, 1))).isEqualByComparingTo("1800");
        }
    }

    @Nested
    class 장_세션 {
        final MarketCalendar cal = new MarketCalendar(List.of(LocalDate.of(2026, 10, 9)));

        @ParameterizedTest
        @CsvSource({"08:29,CLOSED", "08:30,PRE_OPEN_AUCTION", "08:59,PRE_OPEN_AUCTION", "09:00,REGULAR",
                "15:19,REGULAR", "15:20,CLOSING_AUCTION", "15:29,CLOSING_AUCTION", "15:30,CLOSED"})
        void 시간대별_세션(String time, MarketSession expected) {
            LocalDateTime t = LocalDateTime.parse("2026-10-29T" + time);
            assertThat(cal.sessionAt(t)).isEqualTo(expected);
        }

        @Test
        void 휴장일과_주말은_CLOSED() {
            assertThat(cal.sessionAt(LocalDateTime.of(2026, 10, 9, 10, 0))).isEqualTo(MarketSession.CLOSED);
            assertThat(cal.sessionAt(LocalDateTime.of(2026, 10, 31, 10, 0))).isEqualTo(MarketSession.CLOSED);
            assertThat(cal.previousTradingDay(LocalDate.of(2026, 10, 12))).isEqualTo(LocalDate.of(2026, 10, 8));
        }
    }

    @Nested
    class 체결규칙 {
        final OrderBook book = new OrderBook("005930",
                List.of(new OrderBook.Level(d("70100"), 50), new OrderBook.Level(d("70000"), 30)),
                List.of(new OrderBook.Level(d("69900"), 40), new OrderBook.Level(d("69800"), 100)),
                java.time.Instant.now(), false);

        @Test
        void 시장가_매수는_매도호가를_낮은_가격부터_소진() {
            Fill f = MatchingRules.marketBuy(book, 60);
            assertThat(f.quantity()).isEqualTo(60);
            assertThat(f.amount()).isEqualByComparingTo("4203000"); // 30×70,000 + 30×70,100
            assertThat(f.averagePrice()).isEqualByComparingTo("70050");
        }

        @Test
        void 호가_잔량을_넘으면_부분체결() {
            assertThat(MatchingRules.marketBuy(book, 100).quantity()).isEqualTo(80);
        }

        @Test
        void 지정가_매수는_지정가_이하_호가만() {
            assertThat(MatchingRules.limitBuy(book, d("70000"), 50, null).quantity()).isEqualTo(30);
        }

        @Test
        void 구간_저가가_지정가에_닿았으면_지정가로_체결() {
            Fill f = MatchingRules.limitBuy(book, d("69950"), 50, d("69900"));
            assertThat(f.quantity()).isEqualTo(50);
            assertThat(f.amount()).isEqualByComparingTo("3497500");
            assertThat(MatchingRules.limitBuy(book, d("69950"), 50, d("69960")).isEmpty()).isTrue();
        }

        @Test
        void 지정가_매도() {
            assertThat(MatchingRules.limitSell(book, d("69850"), 60, null).quantity()).isEqualTo(40);
        }

        @Test
        void 동시호가_단일가() {
            assertThat(MatchingRules.auction(true, d("70000"), 10, d("70100")).isEmpty()).isTrue();
            assertThat(MatchingRules.auction(false, null, 10, d("70100")).quantity()).isEqualTo(10);
        }

        @Test
        void 같은_패스에서_호가_잔량_중복_소진_방지() {
            OrderBook after = book.consume(MatchingRules.marketBuy(book, 60), true);
            assertThat(MatchingRules.marketBuy(after, 100).quantity()).isEqualTo(20);
        }

        @Test
        void 근사_호가() {
            OrderBook syn = OrderBook.synthetic("x", d("2000"), 1000, java.time.Instant.now());
            assertThat(syn.bestAsk()).isEqualByComparingTo("2000");
            assertThat(syn.bestBid()).isEqualByComparingTo("1999");
            assertThat(syn.asks().get(1).price()).isEqualByComparingTo("2005");
            assertThat(syn.synthetic()).isTrue();
        }
    }
}
