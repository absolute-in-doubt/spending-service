package com.example.spending.service.infrastructure.adapter.out.client.twelvedata;

import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.infrastructure.adapter.out.client.twelvedata.dto.TwelveDataQuoteResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class TwelveDataApiClient implements CurrencyRateApiClient {

    private static final String INTERVAL_DAILY = "1day";

    private final RestClient twelveDataRestClient;
    private final TwelveDataProperties properties;

    @Override
    @CircuitBreaker(name = "twelveData", fallbackMethod = "fetchRateFallback")
    @RateLimiter(name = "twelveData", fallbackMethod = "fetchRateFallback")
    public Optional<ExchangeRate> fetchRate(String currencyFrom, String currencyTo, LocalDate date) {
        String directPair = currencyFrom + "/" + currencyTo;

        // 1. Highest priority: try direct pair currencyFrom/currencyTo (e.g. KZT/USD, RUB/USD)
        Optional<TwelveDataQuoteResponse> directQuote = fetchQuote(directPair);
        if (directQuote.isPresent()) {
            TwelveDataQuoteResponse quote = directQuote.get();
            LocalDate rateDate = resolveRateDate(quote.datetime(), date);
            ExchangeRate rate = ExchangeRate.builder()
                    .currencyPair(directPair)
                    .rateDate(rateDate)
                    .closeRate(quote.effectiveCloseRate())
                    .previousCloseRate(quote.previousClose())
                    .build();

            log.info("Successfully fetched direct exchange rate for {}: close={}, previousClose={}, date={}",
                    directPair, rate.getCloseRate(), rate.getPreviousCloseRate(), rateDate);
            return Optional.of(rate);
        }

        // 2. Fallback: try inverse pair (e.g. USD/KZT, USD/RUB) and invert rate (1 / rate)
        String inversePair = currencyTo + "/" + currencyFrom;
        if (!inversePair.equalsIgnoreCase(directPair)) {
            log.info("Direct rate for {} not available, attempting inverse pair {} from Twelve Data",
                    directPair, inversePair);
            Optional<TwelveDataQuoteResponse> inverseQuote = fetchQuote(inversePair);
            if (inverseQuote.isPresent()) {
                TwelveDataQuoteResponse quote = inverseQuote.get();
                LocalDate rateDate = resolveRateDate(quote.datetime(), date);
                BigDecimal invertedClose = invertRate(quote.effectiveCloseRate());
                BigDecimal invertedPreviousClose = invertRate(quote.previousClose());

                ExchangeRate rate = ExchangeRate.builder()
                        .currencyPair(directPair)
                        .rateDate(rateDate)
                        .closeRate(invertedClose)
                        .previousCloseRate(invertedPreviousClose)
                        .build();

                log.info("Successfully fetched and inverted exchange rate for {} from {}: close={}, previousClose={}, date={}",
                        directPair, inversePair, rate.getCloseRate(), rate.getPreviousCloseRate(), rateDate);
                return Optional.of(rate);
            }
        }

        log.warn("Twelve Data API returned no rates for {} or inverse {}", directPair, inversePair);
        return Optional.empty();
    }

    private Optional<TwelveDataQuoteResponse> fetchQuote(String symbol) {
        var uriBuilder = UriComponentsBuilder.fromPath("/quote")
                .queryParam("symbol", symbol)
                .queryParam("interval", INTERVAL_DAILY)
                .queryParam("apikey", properties.apiKey());

        String uri = uriBuilder.build().toUriString();
        log.debug("Requesting exchange rate quote for symbol {} from Twelve Data: {}", symbol, uri);

        try {
            TwelveDataQuoteResponse response = twelveDataRestClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(TwelveDataQuoteResponse.class);

            if (response == null || response.isError() || !response.hasAnyRate()) {
                log.warn("Twelve Data API returned no rate or error for symbol {}: {}",
                        symbol, response != null ? response.message() : "null response");
                return Optional.empty();
            }

            return Optional.of(response);
        } catch (HttpClientErrorException ex) {
            log.warn("Twelve Data API returned client error {} for symbol {}: {}",
                    ex.getStatusCode(), symbol, ex.getResponseBodyAsString());
            return Optional.empty();
        }
    }

    private BigDecimal invertRate(BigDecimal rawRate) {
        if (rawRate == null || rawRate.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return BigDecimal.ONE.divide(rawRate, 6, RoundingMode.HALF_UP);
    }

    private LocalDate resolveRateDate(String datetime, LocalDate fallbackDate) {
        if (datetime != null && !datetime.isBlank()) {
            try {
                return LocalDate.parse(datetime.substring(0, 10));
            } catch (Exception ignored) {
                // fall back to fallbackDate
            }
        }
        return fallbackDate != null ? fallbackDate : LocalDate.now(ZoneOffset.UTC);
    }

    public Optional<ExchangeRate> fetchRateFallback(String currencyFrom, String currencyTo, LocalDate date, Throwable ex) {
        log.warn("Resilience4j fallback triggered for Twelve Data call ({}/{}, {}): {}",
                currencyFrom, currencyTo, date, ex.getMessage());
        return Optional.empty();
    }
}
