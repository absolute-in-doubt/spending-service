package com.example.spending.service.e2e;

import com.example.spending.service.application.port.in.dto.ExceededTransactionResponseDto;
import com.example.spending.service.application.port.in.dto.TransactionResponseDto;
import com.example.spending.service.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoundaryAndEdgeCasesIntegrationTest extends BaseIntegrationTest {

    private final String client = "0000000124";
    private final String counterparty = "9999999999";

    @Test
    @DisplayName("Should execute Scenario 2 (limit reduction below spent amount)")
    void shouldExecuteScenarioTwoFromTask() {
        // 1.02.2022: Establish limit 1000 USD
        setClockTime("2022-02-01T00:00:00Z");
        postLimit(client, "product", new BigDecimal("1000.00"));

        // 2.02.2022: Tx 500 USD (spent: 500, rem: 500 -> false)
        ResponseEntity<TransactionResponseDto> tx1 = postTransaction(
                client, counterparty, "USD", new BigDecimal("500.00"), "product",
                OffsetDateTime.of(2022, 2, 2, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx1.getBody().limitExceeded()).isFalse();

        // 3.02.2022: Tx 100 USD (spent: 600, rem: 400 -> false)
        ResponseEntity<TransactionResponseDto> tx2 = postTransaction(
                client, counterparty, "USD", new BigDecimal("100.00"), "product",
                OffsetDateTime.of(2022, 2, 3, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx2.getBody().limitExceeded()).isFalse();

        // 10.02.2022: Client reduces limit to 400 USD (spent 600 > 400, rem: -200)
        setClockTime("2022-02-10T00:00:00Z");
        postLimit(client, "product", new BigDecimal("400.00"));

        // 11.02.2022: Tx 100 USD (rem: -300 -> true)
        ResponseEntity<TransactionResponseDto> tx3 = postTransaction(
                client, counterparty, "USD", new BigDecimal("100.00"), "product",
                OffsetDateTime.of(2022, 2, 11, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx3.getBody().limitExceeded()).isTrue();

        // 12.02.2022: Tx 100 USD (rem: -400 -> true)
        ResponseEntity<TransactionResponseDto> tx4 = postTransaction(
                client, counterparty, "USD", new BigDecimal("100.00"), "product",
                OffsetDateTime.of(2022, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx4.getBody().limitExceeded()).isTrue();

        // Query exceeded transactions: exactly 2 transactions from 11 and 12 Feb
        List<ExceededTransactionResponseDto> exceeded = getExceededTransactions(client);
        assertThat(exceeded).hasSize(2);

        assertThat(exceeded.get(0).dateTime()).isEqualTo(OffsetDateTime.of(2022, 2, 11, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(exceeded.get(0).limitSum()).isEqualByComparingTo("400.00");

        assertThat(exceeded.get(1).dateTime()).isEqualTo(OffsetDateTime.of(2022, 2, 12, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(exceeded.get(1).limitSum()).isEqualByComparingTo("400.00");
    }

    @Test
    @DisplayName("Should reset accumulated monthly expenses upon transition to a new month (Requirement 5)")
    void shouldResetMonthlySpendingOnNewMonth() {
        String account = "0000000125";
        setClockTime("2022-01-10T00:00:00Z");
        postLimit(account, "product", new BigDecimal("2000.00"));

        // Jan 31: Customer spends 2500 USD -> limit exceeded
        ResponseEntity<TransactionResponseDto> janTx = postTransaction(
                account, counterparty, "USD", new BigDecimal("2500.00"), "product",
                OffsetDateTime.of(2022, 1, 31, 20, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(janTx.getBody().limitExceeded()).isTrue();

        // Feb 01: Customer spends 100 USD -> counter reset to 0 in new month, limit 2000 carries over -> NOT exceeded
        ResponseEntity<TransactionResponseDto> febTx = postTransaction(
                account, counterparty, "USD", new BigDecimal("100.00"), "product",
                OffsetDateTime.of(2022, 2, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(febTx.getBody().limitExceeded()).isFalse();

        // Feb 15: Customer spends 1900 USD (Total Feb = 2000 -> not exceeded)
        ResponseEntity<TransactionResponseDto> febTx2 = postTransaction(
                account, counterparty, "USD", new BigDecimal("1900.00"), "product",
                OffsetDateTime.of(2022, 2, 15, 12, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(febTx2.getBody().limitExceeded()).isFalse();

        // Feb 16: Customer spends 1 USD (Total Feb = 2001 > 2000 -> exceeded)
        ResponseEntity<TransactionResponseDto> febTx3 = postTransaction(
                account, counterparty, "USD", new BigDecimal("1.00"), "product",
                OffsetDateTime.of(2022, 2, 16, 12, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(febTx3.getBody().limitExceeded()).isTrue();
    }

    @Test
    @DisplayName("Should maintain separate limits for product and service categories")
    void shouldIsolateProductAndServiceCategories() {
        String account = "0000000126";
        setClockTime("2022-03-01T00:00:00Z");
        postLimit(account, "product", new BigDecimal("500.00"));
        postLimit(account, "service", new BigDecimal("2000.00"));

        // Spend 600 on product -> exceeds 500 limit
        ResponseEntity<TransactionResponseDto> productTx = postTransaction(
                account, counterparty, "USD", new BigDecimal("600.00"), "product",
                OffsetDateTime.of(2022, 3, 5, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(productTx.getBody().limitExceeded()).isTrue();

        // Spend 600 on service -> does NOT exceed 2000 limit
        ResponseEntity<TransactionResponseDto> serviceTx = postTransaction(
                account, counterparty, "USD", new BigDecimal("600.00"), "service",
                OffsetDateTime.of(2022, 3, 5, 11, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(serviceTx.getBody().limitExceeded()).isFalse();
    }

    @Test
    @DisplayName("Should apply default 1000 USD limit when customer has not established any limit")
    void shouldApplyDefaultLimitWhenUnset() {
        String account = "0000000127";

        // Tx 1: 999.00 USD -> false
        ResponseEntity<TransactionResponseDto> tx1 = postTransaction(
                account, counterparty, "USD", new BigDecimal("999.00"), "product",
                OffsetDateTime.of(2022, 4, 10, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx1.getBody().limitExceeded()).isFalse();

        // Tx 2: 2.00 USD -> 1001 > 1000 default -> true
        ResponseEntity<TransactionResponseDto> tx2 = postTransaction(
                account, counterparty, "USD", new BigDecimal("2.00"), "product",
                OffsetDateTime.of(2022, 4, 10, 11, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx2.getBody().limitExceeded()).isTrue();

        List<ExceededTransactionResponseDto> exceeded = getExceededTransactions(account);
        assertThat(exceeded).hasSize(1);
        assertThat(exceeded.get(0).limitSum()).isEqualByComparingTo("1000.00");
        assertThat(exceeded.get(0).limitCurrencyShortname()).isEqualTo("USD");
    }
}
