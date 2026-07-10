package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "tip.market")
public record MarketProperties(
        String defaultSymbol,
        String defaultInstrumentKey,
        String defaultTimeframe,
        List<String> supportedTimeframes,
        /**
         * Calendar days of historical candles to request ending yesterday (today comes from
         * the intraday endpoint). Default 30 if null/blank. Overridden per TF by
         * {@link #historicalLookbackDaysByTimeframe}.
         */
        Integer historicalLookbackDays,
        /**
         * Optional per-timeframe lookback (calendar days). Keys: {@code 1m}, {@code 5m}, …
         */
        Map<String, Integer> historicalLookbackDaysByTimeframe
) {

    private static final List<String> DEFAULT_TIMEFRAMES =
            List.of("1m", "5m", "15m", "1h", "4h", "1d");

    /** Defaults tuned for chart depth + Upstox per-request candle caps when chunked. */
    private static final Map<String, Integer> DEFAULT_LOOKBACK_BY_TF = Map.of(
            "1m", 10,
            "5m", 60,
            "15m", 90,
            "1h", 180,
            "4h", 365,
            "1d", 730
    );

    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    public MarketProperties {
        if (supportedTimeframes == null || supportedTimeframes.isEmpty()) {
            supportedTimeframes = DEFAULT_TIMEFRAMES;
        } else {
            supportedTimeframes = List.copyOf(supportedTimeframes);
        }
        if (defaultTimeframe == null || defaultTimeframe.isBlank()) {
            defaultTimeframe = "5m";
        }
        if (historicalLookbackDays == null || historicalLookbackDays < 1) {
            historicalLookbackDays = DEFAULT_LOOKBACK_DAYS;
        }
        if (historicalLookbackDaysByTimeframe == null || historicalLookbackDaysByTimeframe.isEmpty()) {
            historicalLookbackDaysByTimeframe = Map.copyOf(DEFAULT_LOOKBACK_BY_TF);
        } else {
            Map<String, Integer> merged = new HashMap<>(DEFAULT_LOOKBACK_BY_TF);
            historicalLookbackDaysByTimeframe.forEach((k, v) -> {
                if (k != null && v != null && v > 0) {
                    merged.put(k, v);
                }
            });
            historicalLookbackDaysByTimeframe = Map.copyOf(merged);
        }
    }

    public boolean isSupportedTimeframe(String timeframe) {
        return timeframe != null && supportedTimeframes.contains(timeframe);
    }

    /**
     * Calendar-day lookback for historical seed (ending yesterday).
     * Prefer per-timeframe map, else {@link #historicalLookbackDays}.
     */
    public int lookbackDaysFor(String timeframe) {
        if (timeframe != null) {
            Integer byTf = historicalLookbackDaysByTimeframe.get(timeframe);
            if (byTf != null && byTf > 0) {
                return byTf;
            }
        }
        return historicalLookbackDays != null && historicalLookbackDays > 0
                ? historicalLookbackDays
                : DEFAULT_LOOKBACK_DAYS;
    }

    /**
     * Chunk size (calendar days) for multi-request historical fetch.
     * <p>
     * Must respect Upstox History V3 max range (UDAPI1148 if exceeded):
     * <ul>
     *   <li>{@code minutes} interval 1–15 → max <b>1 month</b> per request</li>
     *   <li>{@code minutes} interval &gt; 15 → max <b>1 quarter</b></li>
     *   <li>{@code hours} → max <b>1 quarter</b></li>
     *   <li>{@code days} → max <b>1 decade</b></li>
     * </ul>
     * Also keep chunks small enough for response candle caps (~1000–1500).
     */
    public int historicalChunkDaysFor(String timeframe) {
        return switch (timeframe == null ? "" : timeframe) {
            case "1m" -> 4;   // minutes/1: 1-month cap; small for volume
            case "5m" -> 20;  // minutes/5: 1-month cap
            case "15m" -> 28; // minutes/15: 1-month cap (was 45 → Bad Request)
            case "1h" -> 90;  // hours/1: 1-quarter cap
            case "4h" -> 90;  // hours/4: 1-quarter cap (was 180 → Bad Request)
            case "1d" -> 365; // days/1: decade cap
            default -> 28;
        };
    }
}
