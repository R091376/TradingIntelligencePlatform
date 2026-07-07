package com.tip.market;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public final class NseMarketClock {

    private static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    private NseMarketClock() {
    }

    public static MarketPhase phaseFromClock() {
        ZonedDateTime now = ZonedDateTime.now(CandleBoundaryUtils.NSE_ZONE);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return MarketPhase.CLOSED;
        }

        LocalTime time = now.toLocalTime();
        if (time.isBefore(PRE_OPEN_START) || time.isAfter(MARKET_CLOSE)) {
            return MarketPhase.CLOSED;
        }
        if (time.isBefore(MARKET_OPEN)) {
            return MarketPhase.PRE_OPEN;
        }
        return MarketPhase.OPEN;
    }
}