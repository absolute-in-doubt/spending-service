package com.example.spending.service.infrastructure.adapter.out.client.twelvedata;

import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.infrastructure.adapter.out.client.twelvedata.dto.TwelveDataQuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwelveDataApiClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RestClient restClient;

    private TwelveDataProperties properties;
    private TwelveDataApiClient apiClient;

    private final String pair = "KZT/USD";
    private final String currencyFrom = "KZT";
    private final String currencyTo = "USD";

    @BeforeEach
    void setUp() {
        properties = new TwelveDataProperties("https://api.twelvedata.com", "test-key", Duration.ofSeconds(1), Duration.ofSeconds(2));
        apiClient = new TwelveDataApiClient(restClient, properties);
    }

    @Test
    @DisplayName("Should parse rate successfully when close rate is present")
    void shouldFetchRateWhenCloseIsPresent() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        TwelveDataQuoteResponse response = new TwelveDataQuoteResponse(
                pair, "US Dollar / Kazakhstani Tenge", "2026-10-09",
                new BigDecimal("450.50"), new BigDecimal("449.00"), null, "ok", 200, null
        );

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(response);

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isPresent();
        ExchangeRate rate = result.get();
        assertThat(rate.getCurrencyPair()).isEqualTo(pair);
        assertThat(rate.getRateDate()).isEqualTo(date);
        assertThat(rate.getCloseRate()).isEqualByComparingTo("450.50");
        assertThat(rate.getPreviousCloseRate()).isEqualByComparingTo("449.00");
        assertThat(rate.getEffectiveRate()).isEqualByComparingTo("450.50");
    }

    @Test
    @DisplayName("Should accept response and set closeRate to null when close is missing on weekend but previousClose is present")
    void shouldAcceptPreviousCloseWhenCloseIsNullOnWeekend() {
        LocalDate weekendDate = LocalDate.of(2026, 10, 10);
        TwelveDataQuoteResponse weekendResponse = new TwelveDataQuoteResponse(
                pair, "US Dollar / Kazakhstani Tenge", "2026-10-10",
                null, new BigDecimal("450.50"), null, "ok", 200, null
        );

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(weekendResponse);

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, weekendDate);

        assertThat(result).isPresent();
        ExchangeRate rate = result.get();
        assertThat(rate.getCloseRate()).isNull();
        assertThat(rate.getPreviousCloseRate()).isEqualByComparingTo("450.50");
        assertThat(rate.getEffectiveRate()).isEqualByComparingTo("450.50");
    }

    @Test
    @DisplayName("Should return empty when response contains neither close nor previousClose")
    void shouldReturnEmptyWhenNoRatesInResponse() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        TwelveDataQuoteResponse emptyResponse = new TwelveDataQuoteResponse(
                pair, "US Dollar / Kazakhstani Tenge", "2026-10-10",
                null, null, null, "ok", 200, null
        );

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(emptyResponse);

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty when API returns error status")
    void shouldReturnEmptyWhenApiError() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        TwelveDataQuoteResponse errorResponse = new TwelveDataQuoteResponse(
                pair, null, null,
                null, null, null, "error", 400, "Invalid symbol"
        );

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(errorResponse);

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should fall back to inverse pair (USD/KZT) and invert rates when direct pair (KZT/USD) returns 404")
    void shouldFallbackToInversePairAndInvertRateWhenDirectFails() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        // Direct pair KZT/USD returns 404 client error
        when(restClient.get().uri(org.mockito.ArgumentMatchers.contains("symbol=KZT/USD")).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND));

        // Inverse pair USD/KZT returns 500.00 close, 400.00 previous close
        TwelveDataQuoteResponse inverseResponse = new TwelveDataQuoteResponse(
                "USD/KZT", "US Dollar / Kazakhstani Tenge", "2026-10-09",
                new BigDecimal("500.00"), new BigDecimal("400.00"), null, "ok", 200, null
        );
        when(restClient.get().uri(org.mockito.ArgumentMatchers.contains("symbol=USD/KZT")).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(inverseResponse);

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isPresent();
        ExchangeRate rate = result.get();
        assertThat(rate.getCurrencyPair()).isEqualTo("KZT/USD");
        assertThat(rate.getRateDate()).isEqualTo(date);
        // 1 / 500.00 = 0.002000
        assertThat(rate.getCloseRate()).isEqualByComparingTo("0.002000");
        // 1 / 400.00 = 0.002500
        assertThat(rate.getPreviousCloseRate()).isEqualByComparingTo("0.002500");
    }

    @Test
    @DisplayName("Should return empty gracefully when both direct and inverse queries fail with 4xx client errors")
    void shouldReturnEmptyWhenBothDirectAndInverseFailWithClientError() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new org.springframework.web.client.HttpClientErrorException(org.springframework.http.HttpStatus.NOT_FOUND));

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isEmpty();
    }
}
