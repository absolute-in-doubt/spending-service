package com.example.spending.service.e2e;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.repository.MonthlySpendingRepository;
import com.example.spending.service.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;


class ConcurrencyIntegrationTest extends BaseIntegrationTest {

    private final String client = "0000000131";
    private final String counterparty = "9999999999";

    @Autowired
    private MonthlySpendingRepository monthlySpendingRepository;

    @Test
    @DisplayName("Should correctly evaluate limitExceeded flag and accumulate spending under high concurrency without race conditions")
    void shouldHandleConcurrentTransactionsWithoutRaceConditions() throws InterruptedException {
        setClockTime("2022-05-01T00:00:00Z");
        // Set limit = 1000.00 USD
        postLimit(client, "product", new BigDecimal("1000.00"));

        int threadCount = 10;
        BigDecimal txAmount = new BigDecimal("200.00"); // 10 * 200 = 2000.00 USD total

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<TransactionResponseDto> responses = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        OffsetDateTime txTime = OffsetDateTime.of(2022, 5, 2, 10, 0, 0, 0, ZoneOffset.UTC);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // wait for simultaneous release
                    ResponseEntity<TransactionResponseDto> resp = postTransaction(
                            client, counterparty, "USD", txAmount, "product", txTime
                    );
                    if (resp.getStatusCode() == HttpStatus.OK && resp.getBody() != null) {
                        responses.add(resp.getBody());
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Wait for all threads to be ready, then trigger simultaneous launch
        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();

        // Wait for all threads to complete
        boolean finished = doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errors).isEmpty();
        assertThat(responses).hasSize(threadCount);

        // Under pessimistic locking:
        // Transactions accumulate: 200, 400, 600, 800, 1000 (all <= 1000 -> limitExceeded = false)
        // Then: 1200, 1400, 1600, 1800, 2000 (all > 1000 -> limitExceeded = true)
        long nonExceededCount = responses.stream().filter(r -> !r.limitExceeded()).count();
        long exceededCount = responses.stream().filter(TransactionResponseDto::limitExceeded).count();

        assertThat(nonExceededCount).isEqualTo(5);
        assertThat(exceededCount).isEqualTo(5);

        // Verify total spent aggregate in database
        var spending = monthlySpendingRepository.getAndLock(
                AccountNumber.of(client),
                ExpenseCategory.PRODUCT,
                YearMonth.of(2022, 5)
        );
        assertThat(spending.getTotalSpentUsd()).isEqualByComparingTo("2000.00");
    }
}
