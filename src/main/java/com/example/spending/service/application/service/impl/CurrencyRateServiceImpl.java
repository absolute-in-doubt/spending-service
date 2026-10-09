package com.example.spending.service.application.service.impl;

import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Application service responsible for retrieving and caching currency exchange rates.
 * Implements Cache-Aside pattern: checks DB cache, fetches from external API on miss,
 * and falls back to previous effective rate from DB if unavailable (e.g. weekends/holidays).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyRateServiceImpl {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyRateApiClient currencyRateApiClient;

    /**
     * Retrieves the exchange rate for the specified currency pair and date.
     *
     * @param currencyPair the currency pair (e.g., "KZT/USD")
     * @param date         the date for which the rate is requested
     * @return an Optional containing the ExchangeRate if found or fetched, or Optional.empty()
     *
     * NOTE: in case two threads try to fetch the same exchangeRate from the API and persist it to the DB, one of them
     * will fail with PK constraint violation exception and will fallback to reading an effective rate,
     * which will return the newly rate (the one persisted by the other thread)
     */
    public Optional<ExchangeRate> getOrFetchRate(String currencyPair, LocalDate date) {
        Objects.requireNonNull(currencyPair, "Currency pair must not be null");
        Objects.requireNonNull(date, "Date must not be null");

        // 1. Check exact date match in local DB
        Optional<ExchangeRate> cachedRate = exchangeRateRepository.findByPairAndDate(currencyPair, date);
        if (cachedRate.isPresent()) {
            log.debug("Found cached exchange rate for {} on {}", currencyPair, date);
            return cachedRate;
        }

        // 2. Fetch from external API client
        try {
            Optional<ExchangeRate> fetchedRate = currencyRateApiClient.fetchRate(currencyPair, date);
            if (fetchedRate.isPresent()) {
                ExchangeRate rate = fetchedRate.get();
                exchangeRateRepository.saveOrUpdate(rate);
                log.info("Fetched and cached exchange rate for {} on {}: close={}, prev_close={}",
                        currencyPair, date, rate.getCloseRate(), rate.getPreviousCloseRate());
                return Optional.of(rate);
            }
        } catch (Exception ex) {
            log.warn("Failed to fetch exchange rate from external API for {} on {}: {}",
                    currencyPair, date, ex.getMessage());
        }

        // 3. Fallback to effective rate from DB
        log.info("Rate for {} on {} not available from API, attempting fallback to latest effective rate in DB",
                currencyPair, date);
        return exchangeRateRepository.findEffectiveRate(currencyPair, date);
    }
}
