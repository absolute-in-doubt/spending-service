package com.example.spending.service.e2e;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import com.example.spending.service.domain.repository.TransactionOperationRepository;
import com.example.spending.service.domain.repository.UnprocessedTransactionRepository;
import com.example.spending.service.infrastructure.adapter.in.worker.UnprocessedTransactionWorker;
import com.example.spending.service.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.client.HttpServerErrorException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests verifying foreign currency conversion, weekend/holiday rate fallback,
 * DB rate caching, and DLQ resilience when external API is failing.
 */
@TestPropertySource(properties = "app.dlq.worker.enabled=true")
class CurrencyAndResilienceIntegrationTest extends BaseIntegrationTest {

    private final String client = "0000000128";
    private final String counterparty = "9999999999";

    @Autowired
    private UnprocessedTransactionRepository unprocessedTransactionRepository;

    @Autowired
    private TransactionOperationRepository transactionOperationRepository;

    @Autowired
    private UnprocessedTransactionWorker unprocessedTransactionWorker;

    @Test
    @DisplayName("Should convert KZT to USD using WireMock rate and cache rate in DB")
    void shouldConvertKztToUsdAndCacheInDb() {
        setClockTime("2022-01-15T12:00:00Z");
        postLimit(client, "product", new BigDecimal("1000.00"));

        // Stub KZT/USD rate = 0.0020 (500,000 KZT = 1000.00 USD)
        stubExchangeRate("KZT/USD", "2022-01-15", new BigDecimal("0.0020"), new BigDecimal("0.0019"));

        // Tx 1: 250,000 KZT * 0.0020 = 500.00 USD -> limit not exceeded
        ResponseEntity<TransactionResponseDto> tx1 = postTransaction(
                client, counterparty, "KZT", new BigDecimal("250000.00"), "product",
                OffsetDateTime.of(2022, 1, 15, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx1.getBody().limitExceeded()).isFalse();

        // Verify WireMock was called exactly once for KZT/USD
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/quote")).withQueryParam("symbol", equalTo("KZT/USD")));

        // Tx 2: 260,000 KZT on the SAME day: 500 + 520 = 1020 > 1000 -> limit exceeded
        ResponseEntity<TransactionResponseDto> tx2 = postTransaction(
                client, counterparty, "KZT", new BigDecimal("260000.00"), "product",
                OffsetDateTime.of(2022, 1, 15, 11, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx2.getBody().limitExceeded()).isTrue();

        // WireMock was NOT called again (cache hit in database)
        wireMock.verify(1, getRequestedFor(urlPathEqualTo("/quote")).withQueryParam("symbol", equalTo("KZT/USD")));
    }

    @Test
    @DisplayName("Should fall back to previous_close rate when market is closed on weekend")
    void shouldFallbackToPreviousCloseOnWeekend() {
        String weekendAccount = "0000000129";
        setClockTime("2022-01-16T12:00:00Z"); // Sunday
        postLimit(weekendAccount, "service", new BigDecimal("1000.00"));

        // Weekend: close rate is null, previous_close = 0.0130 (1000 RUB * 0.0130 = 13.00 USD)
        stubExchangeRate("RUB/USD", "2022-01-16", null, new BigDecimal("0.0130"));

        ResponseEntity<TransactionResponseDto> tx = postTransaction(
                weekendAccount, counterparty, "RUB", new BigDecimal("1000.00"), "service",
                OffsetDateTime.of(2022, 1, 16, 14, 0, 0, 0, ZoneOffset.UTC)
        );

        assertThat(tx.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx.getBody().limitExceeded()).isFalse();
    }

    @Test
    @DisplayName("Should park transaction in DLQ when external API fails and recover via worker when rate becomes available")
    void shouldParkInDlqOnApiFailureAndRecoverViaWorker() {
        String dlqAccount = "0000000130";
        setClockTime("2022-01-20T12:00:00Z");
        postLimit(dlqAccount, "product", new BigDecimal("1000.00"));

        // Simulate external API down (503 Service Unavailable)
        stubExchangeRateError("RUB/USD", 503, "Forex service temporarily unavailable");

        OffsetDateTime txTime = OffsetDateTime.of(2022, 1, 20, 10, 0, 0, 0, ZoneOffset.UTC);

        // Transaction should be rejected with 503 Service Unavailable ProblemDetail
        assertThatThrownBy(() ->
                postTransaction(dlqAccount, counterparty, "RUB", new BigDecimal("10000.00"), "product", txTime)
        ).isInstanceOf(HttpServerErrorException.ServiceUnavailable.class);

        // Verify transaction is parked in DLQ table
        assertThat(unprocessedTransactionRepository.hasUnresolvedTransactions(
                com.example.spending.service.domain.model.AccountNumber.of(dlqAccount),
                com.example.spending.service.domain.model.ExpenseCategory.PRODUCT,
                java.time.YearMonth.of(2022, 1)
        )).isTrue();

        // Now external API recovers: returns RUB/USD = 0.0100 (10,000 RUB = 100 USD)
        wireMock.resetAll();
        stubExchangeRate("RUB/USD", "2022-01-20", new BigDecimal("0.0100"), new BigDecimal("0.0099"));

        // Trigger DLQ worker recovery batch
        unprocessedTransactionWorker.processBatch();

        // Verify DLQ is now resolved
        assertThat(unprocessedTransactionRepository.hasUnresolvedTransactions(
                com.example.spending.service.domain.model.AccountNumber.of(dlqAccount),
                com.example.spending.service.domain.model.ExpenseCategory.PRODUCT,
                java.time.YearMonth.of(2022, 1)
        )).isFalse();
    }
}
