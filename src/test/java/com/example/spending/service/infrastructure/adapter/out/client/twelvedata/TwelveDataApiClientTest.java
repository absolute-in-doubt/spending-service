package com.example.spending.service.infrastructure.adapter.out.client.twelvedata;

import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.infrastructure.adapter.out.client.twelvedata.dto.TwelveDataQuoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
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
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should fall back to inverse pair (USD/RUB) and invert rates when direct pair (RUB/USD) returns 422 Unprocessable Entity")
    void shouldFallbackToInversePairWhenDirectPairReturns422() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        // Direct pair RUB/USD returns 422 client error (as observed in production logs)
        when(restClient.get().uri(contains("symbol=RUB/USD")).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        // Inverse pair USD/RUB returns valid quotes
        TwelveDataQuoteResponse inverseResponse = new TwelveDataQuoteResponse(
                "USD/RUB", "US Dollar / Russian Ruble", "2026-10-09",
                new BigDecimal("80.00"), new BigDecimal("75.00"), null, "ok", 200, null
        );
        when(restClient.get().uri(contains("symbol=USD/RUB")).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(inverseResponse);

        Optional<ExchangeRate> result = apiClient.fetchRate("RUB", "USD", date);

        assertThat(result).isPresent();
        ExchangeRate rate = result.get();
        assertThat(rate.getCurrencyPair()).isEqualTo("RUB/USD");
        assertThat(rate.getRateDate()).isEqualTo(date);
        // 1 / 80.00 = 0.012500
        assertThat(rate.getCloseRate()).isEqualByComparingTo("0.012500");
        // 1 / 75.00 = 0.013333
        assertThat(rate.getPreviousCloseRate()).isEqualByComparingTo("0.013333");
    }

    @Test
    @DisplayName("Should return empty gracefully when both direct and inverse queries return 422 Unprocessable Entity")
    void shouldReturnEmptyWhenBothDirectAndInverseReturn422() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNPROCESSABLE_ENTITY));

        Optional<ExchangeRate> result = apiClient.fetchRate("RUB", "USD", date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty gracefully when external API returns 400 Bad Request")
    void shouldReturnEmptyGracefullyOn400BadRequest() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should return empty gracefully when external API returns 401 Unauthorized")
    void shouldReturnEmptyGracefullyOn401Unauthorized() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        when(restClient.get().uri(anyString()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));

        Optional<ExchangeRate> result = apiClient.fetchRate(currencyFrom, currencyTo, date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should fall back to inverse pair when direct pair returns 200 OK with error body (code 422)")
    void shouldFallbackToInversePairWhenDirectReturns200WithErrorBody() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        // Direct pair returns 200 OK with error JSON payload
        TwelveDataQuoteResponse errorResponse = new TwelveDataQuoteResponse(
                "RUB/USD", null, null, null, null, null, "error", 422, "No data"
        );
        when(restClient.get().uri(contains("symbol=RUB/USD")).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(errorResponse);

        TwelveDataQuoteResponse inverseResponse = new TwelveDataQuoteResponse(
                "USD/RUB", "US Dollar / Russian Ruble", "2026-10-09",
                new BigDecimal("80.00"), new BigDecimal("75.00"), null, "ok", 200, null
        );
        when(restClient.get().uri(contains("symbol=USD/RUB")).retrieve().body(TwelveDataQuoteResponse.class))
                .thenReturn(inverseResponse);

        Optional<ExchangeRate> result = apiClient.fetchRate("RUB", "USD", date);

        assertThat(result).isPresent();
        assertThat(result.get().getCurrencyPair()).isEqualTo("RUB/USD");
        assertThat(result.get().getCloseRate()).isEqualByComparingTo("0.012500");
    }

    @Test
    @DisplayName("Should verify that requests contain interval=1day and apikey, and omit date parameter")
    void shouldVerifyUriQueryParamsOnCall() {
        LocalDate date = LocalDate.of(2026, 10, 9);

        ArgumentCaptor<String> uriCaptor = ArgumentCaptor.forClass(String.class);
        when(restClient.get().uri(uriCaptor.capture()).retrieve().body(TwelveDataQuoteResponse.class))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        apiClient.fetchRate(currencyFrom, currencyTo, date);

        List<String> capturedUris = uriCaptor.getAllValues();
        assertThat(capturedUris).hasSize(2);

        // First attempt: direct pair KZT/USD
        assertThat(capturedUris.get(0))
                .contains("symbol=KZT/USD")
                .contains("interval=1day")
                .contains("apikey=test-key")
                .doesNotContain("date=");

        // Second attempt: inverse pair USD/KZT
        assertThat(capturedUris.get(1))
                .contains("symbol=USD/KZT")
                .contains("interval=1day")
                .contains("apikey=test-key")
                .doesNotContain("date=");
    }
}
