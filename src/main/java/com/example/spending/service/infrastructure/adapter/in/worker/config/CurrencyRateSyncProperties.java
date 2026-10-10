package com.example.spending.service.infrastructure.adapter.in.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.currency.sync")
public record CurrencyRateSyncProperties(
        boolean enabled,
        Duration fixedDelay,
        boolean runOnStartup,
        List<String> defaultPairs
) {
    public CurrencyRateSyncProperties {
        if (fixedDelay == null) {
            fixedDelay = Duration.ofHours(1);
        }
        if (defaultPairs == null || defaultPairs.isEmpty()) {
            defaultPairs = List.of("KZT/USD", "RUB/USD");
        }
    }
}
