package com.example.spending.service.infrastructure.adapter.in.worker;


import com.example.spending.service.application.port.in.CurrencyRateService;
import com.example.spending.service.infrastructure.adapter.in.worker.config.CurrencyRateSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Scheduled background worker that retrieves daily exchange rates for default configured currency pairs
 * and persists them into the exchange_rates cache in the database via CurrencyRateService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyRateSyncWorker {

    private final CurrencyRateService currencyRateService;
    private final CurrencyRateSyncProperties properties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        if (properties.enabled() && properties.runOnStartup()) {
            log.info("Running initial currency rate sync on application startup...");
            syncDefaultRates();
        }
    }

    @Scheduled(fixedDelayString = "${app.currency.sync.fixed-delay:1h}")
    public void scheduledSync() {
        if (properties.enabled()) {
            syncDefaultRates();
        }
    }

    public void syncDefaultRates() {
        LocalDate today = LocalDate.now(clock);
        log.info("Syncing default currency rates for date: {}", today);

        for (String pair : properties.defaultPairs()) {
            try {
                currencyRateService.getOrFetchRate(pair, today);
            } catch (Exception ex) {
                log.error("Error occurred while syncing exchange rate for {}: {}", pair, ex.getMessage(), ex);
            }
        }
    }
}
