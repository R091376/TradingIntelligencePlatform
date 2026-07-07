package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tip.market")
public record MarketProperties(
        String defaultSymbol,
        String defaultInstrumentKey,
        String defaultTimeframe
) {
}