package com.tip.indicators;

import com.tip.market.model.Candle;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Simple moving average of candle volume. Pure function — no Spring.
 */
public final class VolumeSmaIndicator {

    public static final int DEFAULT_PERIOD = 20;

    private VolumeSmaIndicator() {
    }

    /**
     * SMA of volume over the last {@code period} candles (including last).
     */
    public static OptionalDouble average(List<Candle> closedAscending, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
        if (closedAscending == null || closedAscending.size() < period) {
            return OptionalDouble.empty();
        }
        int n = closedAscending.size();
        long sum = 0L;
        for (int i = n - period; i < n; i++) {
            sum += closedAscending.get(i).volume();
        }
        return OptionalDouble.of(sum / (double) period);
    }

    /**
     * SMA of volume over the {@code period} bars before the last candle (breakout volume baseline).
     */
    public static OptionalDouble priorAverage(List<Candle> closedAscending, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
        if (closedAscending == null || closedAscending.size() < period + 1) {
            return OptionalDouble.empty();
        }
        int endExclusive = closedAscending.size() - 1;
        int start = endExclusive - period;
        long sum = 0L;
        for (int i = start; i < endExclusive; i++) {
            sum += closedAscending.get(i).volume();
        }
        return OptionalDouble.of(sum / (double) period);
    }
}
