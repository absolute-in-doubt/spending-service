package com.example.spending.service.infrastructure.client.twelvedata;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.currency.twelvedata")
public record TwelveDataProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {
    public TwelveDataProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.twelvedata.com";
        }
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = "demo";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(1);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(2);
        }
    }
}
