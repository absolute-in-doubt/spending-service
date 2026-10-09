package com.example.spending.service.infrastructure.worker;

import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import com.example.spending.service.infrastructure.worker.config.CurrencyRateSyncProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Scheduled background worker that retrieves daily exchange rates for default configured currency pairs
 * and persists them into the exchange_rates cache in the database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyRateSyncWorker {

    private final CurrencyRateApiClient currencyRateApiClient;
    private final ExchangeRateRepository exchangeRateRepository;
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
            syncPair(pair, today);
        }
    }

    private void syncPair(String pair, LocalDate date) {
        try {
            // Check if rate already exists in DB with close rate
            Optional<ExchangeRate> existing = exchangeRateRepository.findByPairAndDate(pair, date);
            if (existing.isPresent() && existing.get().getCloseRate() != null) {
                log.debug("Exchange rate for {} on {} already cached in DB, skipping sync", pair, date);
                return;
            }

            log.debug("Fetching exchange rate for {} on {} via API client", pair, date);
            Optional<ExchangeRate> fetched = currencyRateApiClient.fetchRate(pair, date);
            if (fetched.isPresent()) {
                exchangeRateRepository.saveOrUpdate(fetched.get());
                log.info("Successfully synced exchange rate for {}: close={}, previousClose={}",
                        pair, fetched.get().getCloseRate(), fetched.get().getPreviousCloseRate());
            } else {
                log.warn("Unable to fetch exchange rate for {} on {}", pair, date);
            }
        } catch (Exception ex) {
            log.error("Error occurred while syncing exchange rate for {}: {}", pair, ex.getMessage(), ex);
        }
    }
}
