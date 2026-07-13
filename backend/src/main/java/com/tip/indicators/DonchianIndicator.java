package com.tip.indicators;

import com.tip.market.model.Candle;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Donchian channel high/low over closed candles. Pure function — no Spring.
 *
 * <p>Math: {@code docs/indicators/donchian-20.md}
 */
public final class DonchianIndicator {

    public static final int DEFAULT_PERIOD = 20;

    private DonchianIndicator() {
    }

    /**
     * Highest high of the last {@code period} candles in the list (including the last bar).
     */
    public static OptionalDouble highestHigh(List<Candle> closedAscending, int period) {
        return extremum(closedAscending, period, true, false);
    }

    /**
     * Lowest low of the last {@code period} candles in the list (including the last bar).
     */
    public static OptionalDouble lowestLow(List<Candle> closedAscending, int period) {
        return extremum(closedAscending, period, false, false);
    }

    /**
     * Highest high of the {@code period} bars <em>before</em> the last candle (breakout reference).
     * Requires at least {@code period + 1} candles.
     */
    public static OptionalDouble priorHighestHigh(List<Candle> closedAscending, int period) {
        return extremum(closedAscending, period, true, true);
    }

    /**
     * Lowest low of the {@code period} bars before the last candle (breakdown reference).
     */
    public static OptionalDouble priorLowestLow(List<Candle> closedAscending, int period) {
        return extremum(closedAscending, period, false, true);
    }

    public static OptionalDouble priorHighestHigh(List<Candle> closedAscending) {
        return priorHighestHigh(closedAscending, DEFAULT_PERIOD);
    }

    private static OptionalDouble extremum(
            List<Candle> closedAscending,
            int period,
            boolean high,
            boolean excludeLast
    ) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
        if (closedAscending == null) {
            return OptionalDouble.empty();
        }
        int n = closedAscending.size();
        int endExclusive = excludeLast ? n - 1 : n;
        int start = endExclusive - period;
        if (start < 0 || endExclusive <= start) {
            return OptionalDouble.empty();
        }
        double value = high
                ? closedAscending.get(start).high()
                : closedAscending.get(start).low();
        for (int i = start + 1; i < endExclusive; i++) {
            Candle c = closedAscending.get(i);
            value = high ? Math.max(value, c.high()) : Math.min(value, c.low());
        }
        return OptionalDouble.of(value);
    }
}
