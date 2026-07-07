package com.tip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tip.cors")
public record CorsProperties(String allowedOrigins) {
}