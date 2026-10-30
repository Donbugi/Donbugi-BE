package hongik.finEdu.trading.config;

import hongik.finEdu.trading.market.FeeTaxPolicy;
import hongik.finEdu.trading.market.MarketCalendar;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class TradingMarketConfig {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    /** 테스트에서 고정 시각으로 교체할 수 있도록 Clock 을 빈으로 둔다 */
    @Bean
    public Clock tradingClock() {
        return Clock.system(SEOUL);
    }

    /**
     * mock-trading.holidays: 쉼표 구분 yyyy-MM-dd (주말 외 KRX 휴장일).
     * KRX 공지에 따라 매년 갱신 필요.
     */
    @Bean
    public MarketCalendar marketCalendar(@Value("${mock-trading.holidays:}") String holidaysCsv) {
        List<LocalDate> holidays = new ArrayList<>();
        for (String raw : holidaysCsv.split(",")) {
            String s = raw.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                holidays.add(LocalDate.parse(s));
            } catch (Exception e) {
                log.warn("[mock-trading] 휴장일 파싱 실패: {}", s);
            }
        }
        log.info("[mock-trading] KRX 휴장일 {}건 로드", holidays.size());
        return new MarketCalendar(holidays);
    }

    @Bean
    public FeeTaxPolicy feeTaxPolicy(@Value("${mock-trading.fee-rate:0.00015}") BigDecimal feeRate) {
        return new FeeTaxPolicy(feeRate);
    }
}
