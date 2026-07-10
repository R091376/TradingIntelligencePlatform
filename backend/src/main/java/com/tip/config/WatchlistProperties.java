package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "tip.watchlist")
public record WatchlistProperties(
        List<String> seedSymbols,
        Map<String, String> seedInstrumentKeys,
        int softWarnSize,
        int hardMaxSize
) {

    public WatchlistProperties {
        if (seedSymbols == null) {
            seedSymbols = List.of();
        } else {
            seedSymbols = List.copyOf(seedSymbols);
        }
        if (seedInstrumentKeys == null) {
            seedInstrumentKeys = Map.of();
        } else {
            seedInstrumentKeys = Map.copyOf(seedInstrumentKeys);
        }
        if (softWarnSize <= 0) {
            softWarnSize = 40;
        }
        if (hardMaxSize <= 0) {
            hardMaxSize = 50;
        }
    }
}
