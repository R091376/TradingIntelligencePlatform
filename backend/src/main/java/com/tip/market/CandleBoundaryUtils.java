package com.tip.market;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CandleBoundaryUtils {

    public static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    /**
     * NSE regular equity session open (09:15 IST). Sub-daily candle buckets are
     * aligned to this so live boundaries match Upstox historical/intraday bars
     * (1h → 09:15, 10:15, …; 4h → 09:15, 13:15; 5m/15m unchanged because
     * {@code 555 % interval == 0}).
     */
    public static final int NSE_SESSION_OPEN_MINUTES = 9 * 60 + 15;

    private CandleBoundaryUtils() {
    }

    /**
     * Floor an instant to the start of its candle bucket in NSE zone.
     * <p>
     * Intervals of a full day or longer floor to local midnight. Shorter
     * intervals floor on a grid anchored so that 09:15 IST is always a
     * candle open (matches Upstox NSE bars).
     */
    public static long floorToCandleStartEpochSecond(long epochMillis, int intervalMinutes) {
        if (intervalMinutes <= 0) {
            throw new IllegalArgumentException("intervalMinutes must be positive: " + intervalMinutes);
        }
        ZonedDateTime dateTime = Instant.ofEpochMilli(epochMillis).atZone(NSE_ZONE);
        if (intervalMinutes >= 24 * 60) {
            return dateTime.toLocalDate().atStartOfDay(NSE_ZONE).toEpochSecond();
        }

        int minutesOfDay = dateTime.getHour() * 60 + dateTime.getMinute();
        // origin so session open lands on a boundary (15 for 60m, 75 for 240m, 0 for 5m/15m)
        int origin = Math.floorMod(NSE_SESSION_OPEN_MINUTES, intervalMinutes);
        int shifted = minutesOfDay - origin;
        LocalDate day = dateTime.toLocalDate();

        if (shifted < 0) {
            // Before the first origin-aligned boundary of this calendar day
            // (e.g. 00:00–00:14 when origin=15) → last bucket of previous day.
            day = day.minusDays(1);
            int lastFloored = origin
                    + ((24 * 60 - 1 - origin) / intervalMinutes) * intervalMinutes;
            return day.atStartOfDay(NSE_ZONE).plusMinutes(lastFloored).toEpochSecond();
        }

        int flooredMinutes = origin + (shifted / intervalMinutes) * intervalMinutes;
        return day.atStartOfDay(NSE_ZONE).plusMinutes(flooredMinutes).toEpochSecond();
    }
}
