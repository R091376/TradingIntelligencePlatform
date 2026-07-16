package com.tip.market;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public final class NseMarketClock {

    private static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    /** Regular equity session end (inclusive — market is CLOSED at and after 15:30 IST). */
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private NseMarketClock() {
    }

    public static MarketPhase phaseFromClock() {
        return phaseAt(ZonedDateTime.now(CandleBoundaryUtils.NSE_ZONE));
    }

    /**
     * Resolve phase at an explicit instant (tests + deterministic scheduling).
     * Holidays and weekends are CLOSED. Session: pre-open 09:00–09:15, open until 15:30 exclusive end
     * so 15:30:00 is CLOSED.
     */
    public static MarketPhase phaseAt(ZonedDateTime now) {
        LocalDate day = now.toLocalDate();
        if (!isTradingDay(day)) {
            return MarketPhase.CLOSED;
        }

        LocalTime time = now.toLocalTime();
        // CLOSED before pre-open and at/after 15:30 (inclusive close)
        if (time.isBefore(PRE_OPEN_START) || !time.isBefore(MARKET_CLOSE)) {
            return MarketPhase.CLOSED;
        }
        if (time.isBefore(MARKET_OPEN)) {
            return MarketPhase.PRE_OPEN;
        }
        return MarketPhase.OPEN;
    }

    /** Weekdays that are not on the NSE holiday calendar. */
    public static boolean isTradingDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !NseHolidayCalendar.isHoliday(date);
    }
}