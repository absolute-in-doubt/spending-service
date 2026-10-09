package com.example.spending.service.infrastructure.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.dlq.worker")
public record UnprocessedTransactionWorkerProperties(
        boolean enabled,
        Duration fixedDelay,
        int batchSize,
        Duration leaseDuration,
        int retriesPerAttempt,
        Duration retryBackoff,
        int retriesTotal,
        int threadPoolSize
) {
    public UnprocessedTransactionWorkerProperties {
        if (fixedDelay == null) {
            fixedDelay = Duration.ofSeconds(10);
        }
        if (batchSize <= 0) {
            batchSize = 10;
        }
        if (leaseDuration == null) {
            leaseDuration = Duration.ofSeconds(30);
        }
        if (retriesPerAttempt <= 0) {
            retriesPerAttempt = 3;
        }
        if (retryBackoff == null) {
            retryBackoff = Duration.ofMillis(500);
        }
        if (retriesTotal <= 0) {
            retriesTotal = 5;
        }
        if (threadPoolSize <= 0) {
            threadPoolSize = 2;
        }
    }
}
