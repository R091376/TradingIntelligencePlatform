package com.tip.market;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CandleBoundaryUtilsTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static long epochMs(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), IST).toInstant().toEpochMilli();
    }

    private static long floorSec(int y, int m, int d, int h, int min, int intervalMinutes) {
        return CandleBoundaryUtils.floorToCandleStartEpochSecond(
                epochMs(y, m, d, h, min), intervalMinutes);
    }

    private static long expectedSec(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(LocalDateTime.of(y, m, d, h, min), IST).toEpochSecond();
    }

    @Test
    void floorsFifteenMinutes() {
        assertEquals(expectedSec(2026, 7, 10, 10, 15), floorSec(2026, 7, 10, 10, 17, 15));
        assertEquals(expectedSec(2026, 7, 10, 10, 30), floorSec(2026, 7, 10, 10, 44, 15));
    }

    @Test
    void floorsOneHour() {
        assertEquals(expectedSec(2026, 7, 10, 10, 0), floorSec(2026, 7, 10, 10, 45, 60));
        assertEquals(expectedSec(2026, 7, 10, 11, 0), floorSec(2026, 7, 10, 11, 0, 60));
    }

    @Test
    void floorsFourHoursAcrossHoursOfDay() {
        // 09:15 → minutes-of-day 555 → 555/240*240 = 480 → 08:00
        assertEquals(expectedSec(2026, 7, 10, 8, 0), floorSec(2026, 7, 10, 9, 15, 240));
        // 12:30 → 750/240*240 = 720 → 12:00
        assertEquals(expectedSec(2026, 7, 10, 12, 0), floorSec(2026, 7, 10, 12, 30, 240));
        // 16:00 → 960/240*240 = 960 → 16:00
        assertEquals(expectedSec(2026, 7, 10, 16, 0), floorSec(2026, 7, 10, 16, 10, 240));
    }

    @Test
    void floorsDailyToMidnight() {
        assertEquals(expectedSec(2026, 7, 10, 0, 0), floorSec(2026, 7, 10, 15, 30, 24 * 60));
    }
}
