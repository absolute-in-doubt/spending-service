package com.example.spending.service.application.port.out;

import com.example.spending.service.domain.model.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Outbound port for interacting with external financial exchange rate APIs (Twelve Data, Alpha Vantage, etc.).
 */
public interface CurrencyRateApiClient {

    /**
     * Fetches daily exchange rate for the specified currency pair and date from an external provider.
     *
     * @param currencyFrom currency from which we want to exchange
     * @param currencyTo currency to which we exchange
     * @param date         date of the exchange rate
     * @return Optional containing the fetched ExchangeRate (with close rate and optional previous close rate),
     *         or Optional.empty() if the rate was not found
     */
    Optional<ExchangeRate> fetchRate(String currencyFrom, String currencyTo, LocalDate date);
}
