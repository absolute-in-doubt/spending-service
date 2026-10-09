package com.example.spending.service.infrastructure.worker.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(UnprocessedTransactionWorkerProperties.class)
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler(UnprocessedTransactionWorkerProperties properties) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(properties.threadPoolSize());
        scheduler.setThreadNamePrefix("dlq-worker-");
        scheduler.initialize();
        return scheduler;
    }
}
