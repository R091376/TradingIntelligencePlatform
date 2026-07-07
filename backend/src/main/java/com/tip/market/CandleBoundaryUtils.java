package com.tip.market;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class CandleBoundaryUtils {

    public static final ZoneId NSE_ZONE = ZoneId.of("Asia/Kolkata");

    private CandleBoundaryUtils() {
    }

    public static long floorToCandleStartEpochSecond(long epochMillis, int intervalMinutes) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(epochMillis).atZone(NSE_ZONE);
        int flooredMinute = (dateTime.getMinute() / intervalMinutes) * intervalMinutes;
        return dateTime
                .withMinute(flooredMinute)
                .withSecond(0)
                .withNano(0)
                .toEpochSecond();
    }
}