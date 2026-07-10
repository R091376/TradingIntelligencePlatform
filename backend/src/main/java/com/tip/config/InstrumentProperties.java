package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Upstox instrument master cache settings (resolved/used fully in PR2).
 */
@ConfigurationProperties(prefix = "tip.instruments")
public record InstrumentProperties(
        String masterUrl,
        String cacheDir,
        boolean refreshOnStartup
) {
}
