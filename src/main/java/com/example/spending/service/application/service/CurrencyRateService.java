package com.example.spending.service.application.service;

import com.example.spending.service.domain.model.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Inbound port for retrieving and caching currency exchange rates.
 */
public interface CurrencyRateService {

    /**
     * Retrieves the exchange rate for the specified currency pair and date.
     *
     * @param currencyPair the currency pair (e.g., "KZT/USD")
     * @param date         the date for which the rate is requested
     * @return an Optional containing the ExchangeRate if found or fetched, or Optional.empty()
     */
    Optional<ExchangeRate> getOrFetchRate(String currencyPair, LocalDate date);
}
