package com.example.spending.service.infrastructure.client.twelvedata;

import com.example.spending.service.application.port.CurrencyRateApiClient;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.infrastructure.client.twelvedata.dto.TwelveDataQuoteResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwelveDataApiClient implements CurrencyRateApiClient {

    private final RestClient twelveDataRestClient;
    private final TwelveDataProperties properties;

    @Override
    @CircuitBreaker(name = "twelveData", fallbackMethod = "fetchRateFallback")
    @RateLimiter(name = "twelveData", fallbackMethod = "fetchRateFallback")
    public Optional<ExchangeRate> fetchRate(String currencyPair, LocalDate date) {
        var uriBuilder = UriComponentsBuilder.fromPath("/quote")
                .queryParam("symbol", currencyPair)
                .queryParam("apikey", properties.apiKey());

        if (date != null) {
            uriBuilder.queryParam("date", date.toString());
        }

        String uri = uriBuilder.build().toUriString();
        log.debug("Requesting exchange rate for {} from Twelve Data: {}", currencyPair, uri);

        TwelveDataQuoteResponse response = twelveDataRestClient.get()
                .uri(uri)
                .retrieve()
                .body(TwelveDataQuoteResponse.class);

        if (response == null || response.isError() || response.effectiveCloseRate() == null) {
            log.warn("Twelve Data API returned no rate or error for {}: {}",
                    currencyPair, response != null ? response.message() : "null response");
            return Optional.empty();
        }

        LocalDate rateDate = date;
        if (response.datetime() != null && !response.datetime().isBlank()) {
            try {
                rateDate = LocalDate.parse(response.datetime().substring(0, 10));
            } catch (Exception ignored) {
                // keep date parameter as fallback
            }
        }
        if (rateDate == null) {
            rateDate = LocalDate.now(ZoneOffset.UTC);
        }

        ExchangeRate exchangeRate = ExchangeRate.builder()
                .currencyPair(currencyPair)
                .rateDate(rateDate)
                .closeRate(response.effectiveCloseRate())
                .previousCloseRate(response.previousClose())
                .build();

        log.info("Successfully fetched exchange rate for {}: close={}, previousClose={}, date={}",
                currencyPair, exchangeRate.getCloseRate(), exchangeRate.getPreviousCloseRate(), rateDate);

        return Optional.of(exchangeRate);
    }

    public Optional<ExchangeRate> fetchRateFallback(String currencyPair, LocalDate date, Throwable ex) {
        log.warn("Resilience4j fallback triggered for Twelve Data call ({}, {}): {}", currencyPair, date, ex.getMessage());
        return Optional.empty();
    }
}
