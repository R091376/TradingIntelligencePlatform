package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstox instrument master cache settings.
 * Bound from {@code tip.instruments.*} (master-url, cache-dir, refresh-on-startup).
 */
@ConfigurationProperties(prefix = "tip.instruments")
public record InstrumentProperties(
        String masterUrl,
        String cacheDir,
        boolean refreshOnStartup
) {

    private static final String DEFAULT_MASTER_URL =
            "https://assets.upstox.com/market-quote/instruments/exchange/NSE.json.gz";

    public InstrumentProperties {
        if (masterUrl == null || masterUrl.isBlank()) {
            masterUrl = DEFAULT_MASTER_URL;
        }
        if (cacheDir == null || cacheDir.isBlank()) {
            cacheDir = System.getProperty("java.io.tmpdir") + "/tip-instruments";
        }
    }
}
