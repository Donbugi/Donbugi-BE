package hongik.finEdu.trading.service;

import hongik.finEdu.trading.market.MarketCalendar;
import hongik.finEdu.trading.market.MarketSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 현재 시각 기준 장 상태 (기존 MarketHoursService 대체: 휴장일·동시호가 반영) */
@Service
@RequiredArgsConstructor
public class MarketClock {

    private final Clock clock;
    private final MarketCalendar calendar;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public MarketSession session() {
        return calendar.sessionAt(now());
    }

    /** 장 시작 동시호가 ~ 장 마감까지 (주문 접수 가능) */
    public boolean isMarketOpen() {
        return session().acceptsOrders();
    }

    public boolean isTradingDay() {
        return calendar.isTradingDay(today());
    }

    public MarketCalendar calendar() {
        return calendar;
    }
}
