package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "tip.market")
public record MarketProperties(
        String defaultSymbol,
        String defaultInstrumentKey,
        String defaultTimeframe,
        List<String> supportedTimeframes
) {

    private static final List<String> DEFAULT_TIMEFRAMES =
            List.of("1m", "5m", "15m", "1h", "4h", "1d");

    public MarketProperties {
        if (supportedTimeframes == null || supportedTimeframes.isEmpty()) {
            supportedTimeframes = DEFAULT_TIMEFRAMES;
        } else {
            supportedTimeframes = List.copyOf(supportedTimeframes);
        }
        if (defaultTimeframe == null || defaultTimeframe.isBlank()) {
            defaultTimeframe = "5m";
        }
    }

    public boolean isSupportedTimeframe(String timeframe) {
        return timeframe != null && supportedTimeframes.contains(timeframe);
    }
}
