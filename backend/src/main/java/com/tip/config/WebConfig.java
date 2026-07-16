package com.tip.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties({
        UpstoxProperties.class,
        MarketProperties.class,
        CorsProperties.class,
        WatchlistProperties.class,
        InstrumentProperties.class,
        PatternProperties.class,
        UserProperties.class
})
public class WebConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    public WebConfig(CorsProperties corsProperties) {
        this.corsProperties = corsProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Credentials required for session cookie login (must not use * origin).
        registry.addMapping("/api/**")
                .allowedOrigins(corsProperties.allowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
