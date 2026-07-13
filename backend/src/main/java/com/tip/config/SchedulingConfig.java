package com.tip.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring {@code @Scheduled} tasks (e.g. market phase clock reconciler).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
