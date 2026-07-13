package com.tip.indicators;

import com.tip.market.model.Candle;

import java.util.List;
import java.util.OptionalDouble;

/**
 * Wilder Average True Range. Pure function — no Spring.
 *
 * <p>Math: {@code docs/indicators/atr-14.md}
 */
public final class AtrIndicator {

    public static final int DEFAULT_PERIOD = 14;

    private AtrIndicator() {
    }

    /**
     * Latest ATR for the series, or empty if fewer than {@code period + 1} candles.
     */
    public static OptionalDouble latest(List<Candle> closedAscending, int period) {
        if (period < 1) {
            throw new IllegalArgumentException("period must be >= 1");
        }
        if (closedAscending == null || closedAscending.size() < period + 1) {
            return OptionalDouble.empty();
        }

        double atr = 0.0;
        // First ATR = SMA of first `period` true ranges (bars 1..period using prev closes 0..period-1)
        for (int i = 1; i <= period; i++) {
            atr += trueRange(closedAscending.get(i), closedAscending.get(i - 1).close());
        }
        atr /= period;

        for (int i = period + 1; i < closedAscending.size(); i++) {
            double tr = trueRange(closedAscending.get(i), closedAscending.get(i - 1).close());
            atr = (atr * (period - 1) + tr) / period;
        }
        return OptionalDouble.of(atr);
    }

    public static OptionalDouble latest(List<Candle> closedAscending) {
        return latest(closedAscending, DEFAULT_PERIOD);
    }

    static double trueRange(Candle bar, double previousClose) {
        double hl = bar.high() - bar.low();
        double hc = Math.abs(bar.high() - previousClose);
        double lc = Math.abs(bar.low() - previousClose);
        return Math.max(hl, Math.max(hc, lc));
    }
}
