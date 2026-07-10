package com.tip.market;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CandleBoundaryUtils {

    public static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    private CandleBoundaryUtils() {
    }

    /**
     * Floor an instant to the start of its candle bucket in NSE zone.
     * <p>
     * Uses minutes-of-day so multi-hour intervals (e.g. 4h = 240) work. Intervals
     * of a full day or longer floor to local midnight.
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
        int floored = (minutesOfDay / intervalMinutes) * intervalMinutes;
        return dateTime
                .withHour(floored / 60)
                .withMinute(floored % 60)
                .withSecond(0)
                .withNano(0)
                .toEpochSecond();
    }
}