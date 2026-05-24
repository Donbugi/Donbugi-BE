package hongik.finEdu.trading.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static hongik.finEdu.trading.domain.TradingConstants.*;

@Service
public class MarketHoursService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public boolean isMarketOpen() {
        return isMarketOpen(LocalDateTime.now(SEOUL));
    }

    public boolean isMarketOpen(LocalDateTime dateTime) {
        LocalDate date = dateTime.toLocalDate();
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = dateTime.toLocalTime();
        LocalTime open = LocalTime.of(MARKET_OPEN_HOUR, MARKET_OPEN_MINUTE);
        LocalTime close = LocalTime.of(MARKET_CLOSE_HOUR, MARKET_CLOSE_MINUTE);
        return !time.isBefore(open) && !time.isAfter(close);
    }
}
