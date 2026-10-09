package com.example.spending.service.infrastructure.worker;

import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.application.service.TransactionRecoveryHandler;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.UnprocessedTransaction;
import com.example.spending.service.domain.model.UnprocessedTransactionStatus;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import com.example.spending.service.domain.repository.UnprocessedTransactionRepository;
import com.example.spending.service.infrastructure.worker.config.UnprocessedTransactionWorkerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnprocessedTransactionWorkerTest {

    @Mock
    private UnprocessedTransactionRepository repository;

    @Mock
    private CurrencyRateApiClient currencyRateApiClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private TransactionRecoveryHandler recoveryHandler;

    private UnprocessedTransactionWorkerProperties properties;
    private UnprocessedTransactionWorker worker;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        properties = new UnprocessedTransactionWorkerProperties(
                true,
                Duration.ofSeconds(10),
                10,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO, // zero backoff for instant tests
                3,
                2
        );

        worker = new UnprocessedTransactionWorker(
                repository,
                currencyRateApiClient,
                exchangeRateRepository,
                properties,
                recoveryHandler
        );
    }

    private UnprocessedTransaction createSampleUnprocessedTransaction(int retryCount) {
        UnprocessedTransaction record = UnprocessedTransaction.create(
                AccountNumber.of("0000000123"),
                AccountNumber.of("9999999999"),
                CurrencyCode.of("KZT"),
                new BigDecimal("5000.00"),
                ExpenseCategory.PRODUCT,
                now,
                "Currency rate unavailable for KZT/USD"
        );
        record.setId(100L);
        record.setRetryCount(retryCount);
        return record;
    }

    @Test
    @DisplayName("Should successfully fetch rate, save to DB, call recovery handler, and mark record PROCESSED")
    void shouldProcessSuccessfullyAndCallRecoveryHandler() {
        UnprocessedTransaction record = createSampleUnprocessedTransaction(0);
        when(repository.acquireBatchForRetry(anyString(), any(), any(), eq(3), eq(10)))
                .thenReturn(List.of(record));

        ExchangeRate rate = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(now.toLocalDate())
                .closeRate(new BigDecimal("0.0021"))
                .previousCloseRate(new BigDecimal("0.0020"))
                .build();

        when(currencyRateApiClient.fetchRate("KZT/USD", now.toLocalDate())).thenReturn(Optional.of(rate));

        worker.processBatch();

        verify(exchangeRateRepository).saveOrUpdate(rate);
        verify(recoveryHandler).completeRecoveredTransaction(record);
        assertThat(record.getStatus()).isEqualTo(UnprocessedTransactionStatus.PROCESSED);
        assertThat(record.getLeaseToken()).isNull();
        verify(repository).update(record);
    }

    @Test
    @DisplayName("Should increment retry count and keep PENDING when external rate is unavailable")
    void shouldIncrementRetryWhenRateUnavailable() {
        UnprocessedTransaction record = createSampleUnprocessedTransaction(0);
        when(repository.acquireBatchForRetry(anyString(), any(), any(), eq(3), eq(10)))
                .thenReturn(List.of(record));

        when(currencyRateApiClient.fetchRate("KZT/USD", now.toLocalDate())).thenReturn(Optional.empty());

        worker.processBatch();

        // 2 in-memory attempts configured
        verify(currencyRateApiClient, times(2)).fetchRate("KZT/USD", now.toLocalDate());
        verifyNoInteractions(exchangeRateRepository);
        verifyNoInteractions(recoveryHandler);

        assertThat(record.getRetryCount()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo(UnprocessedTransactionStatus.PENDING);
        assertThat(record.getLeaseToken()).isNull();
        verify(repository).update(record);
    }

    @Test
    @DisplayName("Should mark FAILED when retriesTotal limit is reached")
    void shouldMarkFailedWhenTotalRetriesExceeded() {
        // Record is on its 2nd retry, and retriesTotal = 3
        UnprocessedTransaction record = createSampleUnprocessedTransaction(2);
        when(repository.acquireBatchForRetry(anyString(), any(), any(), eq(3), eq(10)))
                .thenReturn(List.of(record));

        when(currencyRateApiClient.fetchRate("KZT/USD", now.toLocalDate())).thenReturn(Optional.empty());

        worker.processBatch();

        verifyNoInteractions(exchangeRateRepository);
        verifyNoInteractions(recoveryHandler);

        assertThat(record.getStatus()).isEqualTo(UnprocessedTransactionStatus.FAILED);
        assertThat(record.getLeaseToken()).isNull();
        verify(repository).update(record);
    }

    @Test
    @DisplayName("Should skip batch execution when worker is disabled")
    void shouldSkipExecutionWhenDisabled() {
        UnprocessedTransactionWorkerProperties disabledProps = new UnprocessedTransactionWorkerProperties(
                false,
                Duration.ofSeconds(10),
                10,
                Duration.ofSeconds(30),
                2,
                Duration.ZERO,
                3,
                2
        );
        UnprocessedTransactionWorker disabledWorker = new UnprocessedTransactionWorker(
                repository,
                currencyRateApiClient,
                exchangeRateRepository,
                disabledProps,
                recoveryHandler
        );

        disabledWorker.processBatch();

        verifyNoInteractions(repository);
        verifyNoInteractions(currencyRateApiClient);
    }
}
