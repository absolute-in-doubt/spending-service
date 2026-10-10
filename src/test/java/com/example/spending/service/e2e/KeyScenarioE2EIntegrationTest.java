package com.example.spending.service.e2e;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExceededTransactionResponseDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExpenseLimitResponseDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
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

/**
 * End-to-End API test reproducing Key Scenario 1 ("1 случай") from Task.pdf Table (Requirement 6).
 *
 * Sequence:
 * 1. 2022-01-01: Set limit 1000.00 USD (product)
 * 2. 2022-01-02: Tx 500.00 USD -> limit_exceeded = false (balance: 500)
 * 3. 2022-01-03: Tx 600.00 USD -> limit_exceeded = true  (balance: -100)
 * 4. 2022-01-10: Set new limit 2000.00 USD (product)     (balance: 900)
 * 5. 2022-01-11: Tx 100.00 USD -> limit_exceeded = false (balance: 800)
 * 6. 2022-01-12: Tx 700.00 USD -> limit_exceeded = false (balance: 100)
 * 7. 2022-01-13: Tx 100.00 USD -> limit_exceeded = false (balance: 0, boundary: exactly 0 is NOT exceeded)
 * 8. 2022-01-13: Tx 100.00 USD -> limit_exceeded = true  (balance: -100)
 * 9. GET /api/client/transactions/exceeded: returns exactly the 2 transactions from Jan 3 and Jan 13.
 */
class KeyScenarioE2EIntegrationTest extends BaseIntegrationTest {

    private final String clientAccount = "0000000123";
    private final String counterpartyAccount = "9999999999";
    private final String category = "product";

    @Test
    @DisplayName("Should fully execute Scenario 1 from Task.pdf via HTTP API and return correct exceeded transactions")
    void shouldExecuteScenarioOneViaHttp() {
        // Step 1: 01.01.2022 - Establish initial limit 1000 USD
        setClockTime("2022-01-01T00:00:00Z");
        ResponseEntity<ExpenseLimitResponseDto> limit1Resp = postLimit(clientAccount, category, new BigDecimal("1000.00"));
        assertThat(limit1Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(limit1Resp.getBody()).isNotNull();
        assertThat(limit1Resp.getBody().limitSum()).isEqualByComparingTo("1000.00");
        assertThat(limit1Resp.getBody().limitDateTime()).isEqualTo(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));

        // Step 2: 02.01.2022 - Transaction 500 USD (spent: 500, remaining: 500 -> false)
        ResponseEntity<TransactionResponseDto> tx1 = postTransaction(
                clientAccount, counterpartyAccount, "USD",
                new BigDecimal("500.00"), category,
                OffsetDateTime.of(2022, 1, 2, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx1.getBody()).isNotNull();
        assertThat(tx1.getBody().limitExceeded()).isFalse();

        // Step 3: 03.01.2022 - Transaction 600 USD (spent: 1100, remaining: -100 -> true)
        ResponseEntity<TransactionResponseDto> tx2 = postTransaction(
                clientAccount, counterpartyAccount, "USD",
                new BigDecimal("600.00"), category,
                OffsetDateTime.of(2022, 1, 3, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx2.getBody()).isNotNull();
        assertThat(tx2.getBody().limitExceeded()).isTrue();

        // Step 4: 10.01.2022 - Establish new limit 2000 USD (clock set to 10.01.2022)
        setClockTime("2022-01-10T00:00:00Z");
        ResponseEntity<ExpenseLimitResponseDto> limit2Resp = postLimit(clientAccount, category, new BigDecimal("2000.00"));
        assertThat(limit2Resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(limit2Resp.getBody()).isNotNull();
        assertThat(limit2Resp.getBody().limitSum()).isEqualByComparingTo("2000.00");
        assertThat(limit2Resp.getBody().limitDateTime()).isEqualTo(OffsetDateTime.of(2022, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));

        // Step 5: 11.01.2022 - Transaction 100 USD (spent: 1200, remaining: 800 -> false)
        ResponseEntity<TransactionResponseDto> tx3 = postTransaction(
                clientAccount, counterpartyAccount, "USD",
                new BigDecimal("100.00"), category,
                OffsetDateTime.of(2022, 1, 11, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx3.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx3.getBody()).isNotNull();
        assertThat(tx3.getBody().limitExceeded()).isFalse();

        // Step 6: 12.01.2022 - Transaction 700 USD (spent: 1900, remaining: 100 -> false)
        ResponseEntity<TransactionResponseDto> tx4 = postTransaction(
                clientAccount, counterpartyAccount, "USD",
                new BigDecimal("700.00"), category,
                OffsetDateTime.of(2022, 1, 12, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx4.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx4.getBody()).isNotNull();
        assertThat(tx4.getBody().limitExceeded()).isFalse();

        // Step 7: 13.01.2022 - Transaction 100 USD (spent: 2000, remaining: 0 -> false)
        ResponseEntity<TransactionResponseDto> tx5 = postTransaction(
                clientAccount, counterpartyAccount, "USD",
                new BigDecimal("100.00"), category,
                OffsetDateTime.of(2022, 1, 13, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx5.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx5.getBody()).isNotNull();
        assertThat(tx5.getBody().limitExceeded()).isFalse();

        // Step 8: 13.01.2022 - Transaction 100 USD (spent: 2100, remaining: -100 -> true)
        ResponseEntity<TransactionResponseDto> tx6 = postTransaction(
                clientAccount, counterpartyAccount, "USD",
                new BigDecimal("100.00"), category,
                OffsetDateTime.of(2022, 1, 13, 15, 0, 0, 0, ZoneOffset.UTC)
        );
        assertThat(tx6.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx6.getBody()).isNotNull();
        assertThat(tx6.getBody().limitExceeded()).isTrue();

        // Step 9: Query exceeded transactions list via HTTP
        List<ExceededTransactionResponseDto> exceededList = getExceededTransactions(clientAccount);

        // Verify strictly 2 transactions returned: from 3 Jan and 13 Jan
        assertThat(exceededList).hasSize(2);

        // Exceeded Tx 1: Jan 3, under Limit 1 (1000 USD)
        ExceededTransactionResponseDto firstExceeded = exceededList.get(0);
        assertThat(firstExceeded.accountFrom()).isEqualTo(clientAccount);
        assertThat(firstExceeded.sum()).isEqualByComparingTo("600.00");
        assertThat(firstExceeded.currencyShortname()).isEqualTo("USD");
        assertThat(firstExceeded.expenseCategory()).isEqualTo("product");
        assertThat(firstExceeded.dateTime()).isEqualTo(OffsetDateTime.of(2022, 1, 3, 10, 0, 0, 0, ZoneOffset.UTC));
        assertThat(firstExceeded.limitSum()).isEqualByComparingTo("1000.00");
        assertThat(firstExceeded.limitDateTime()).isEqualTo(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(firstExceeded.limitCurrencyShortname()).isEqualTo("USD");

        // Exceeded Tx 2: Jan 13, under Limit 2 (2000 USD)
        ExceededTransactionResponseDto secondExceeded = exceededList.get(1);
        assertThat(secondExceeded.accountFrom()).isEqualTo(clientAccount);
        assertThat(secondExceeded.sum()).isEqualByComparingTo("100.00");
        assertThat(secondExceeded.currencyShortname()).isEqualTo("USD");
        assertThat(secondExceeded.expenseCategory()).isEqualTo("product");
        assertThat(secondExceeded.dateTime()).isEqualTo(OffsetDateTime.of(2022, 1, 13, 15, 0, 0, 0, ZoneOffset.UTC));
        assertThat(secondExceeded.limitSum()).isEqualByComparingTo("2000.00");
        assertThat(secondExceeded.limitDateTime()).isEqualTo(OffsetDateTime.of(2022, 1, 10, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(secondExceeded.limitCurrencyShortname()).isEqualTo("USD");
    }
}
