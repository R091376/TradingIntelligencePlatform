package com.tip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executors so heavy pattern evaluation does not block the Upstox feed thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String PATTERN_TASK_EXECUTOR = "patternTaskExecutor";

    @Bean(name = PATTERN_TASK_EXECUTOR)
    public Executor patternTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("pattern-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
