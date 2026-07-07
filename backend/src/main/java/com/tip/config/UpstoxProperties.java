package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tip.upstox")
public record UpstoxProperties(String accessToken) {
}