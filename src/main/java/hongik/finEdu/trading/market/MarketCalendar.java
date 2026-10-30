package hongik.finEdu.trading.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * KRX 영업일·세션 판단. 휴장일은 설정(mock-trading.holidays)으로 주입한다.
 */
public class MarketCalendar {

    public static final LocalTime PRE_OPEN = LocalTime.of(8, 30);
    public static final LocalTime OPEN = LocalTime.of(9, 0);
    public static final LocalTime CLOSING_AUCTION = LocalTime.of(15, 20);
    public static final LocalTime CLOSE = LocalTime.of(15, 30);

    private final Set<LocalDate> holidays;

    public MarketCalendar(Collection<LocalDate> holidays) {
        this.holidays = new TreeSet<>(holidays);
    }

    public boolean isTradingDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(date);
    }

    public MarketSession sessionAt(LocalDateTime t) {
        if (!isTradingDay(t.toLocalDate())) {
            return MarketSession.CLOSED;
        }
        LocalTime time = t.toLocalTime();
        if (time.isBefore(PRE_OPEN)) {
            return MarketSession.CLOSED;
        }
        if (time.isBefore(OPEN)) {
            return MarketSession.PRE_OPEN_AUCTION;
        }
        if (time.isBefore(CLOSING_AUCTION)) {
            return MarketSession.REGULAR;
        }
        if (time.isBefore(CLOSE)) {
            return MarketSession.CLOSING_AUCTION;
        }
        return MarketSession.CLOSED;
    }

    public LocalDate previousTradingDay(LocalDate date) {
        LocalDate d = date.minusDays(1);
        while (!isTradingDay(d)) {
            d = d.minusDays(1);
        }
        return d;
    }
}
