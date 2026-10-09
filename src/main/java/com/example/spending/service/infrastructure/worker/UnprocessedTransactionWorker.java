package com.example.spending.service.infrastructure.worker;


import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.model.UnprocessedTransaction;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import com.example.spending.service.domain.repository.UnprocessedTransactionRepository;
import com.example.spending.service.infrastructure.worker.config.UnprocessedTransactionWorkerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Scheduled background worker that polls unprocessable transactions (DLQ),
 * attempts to fetch missing exchange rates with in-memory retries,
 * and handles leasing and total retry limits before marking records as FAILED.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnprocessedTransactionWorker {

    private final UnprocessedTransactionRepository repository;
    private final CurrencyRateApiClient currencyRateApiClient;
    private final ExchangeRateRepository exchangeRateRepository;
    private final UnprocessedTransactionWorkerProperties properties;
    private final ObjectProvider<TransactionRecoveryHandler> recoveryHandlerProvider;

    @Scheduled(fixedDelayString = "${app.dlq.worker.fixed-delay:10s}")
    public void processBatch() {
        if (!properties.enabled()) {
            return;
        }

        String workerToken = "worker-" + UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime leaseUntil = now.plus(properties.leaseDuration());

        List<UnprocessedTransaction> batch = repository.acquireBatchForRetry(
                workerToken,
                now,
                leaseUntil,
                properties.retriesTotal(),
                properties.batchSize()
        );

        if (batch.isEmpty()) {
            return;
        }

        log.info("Worker {} acquired batch of {} unprocessed transactions", workerToken, batch.size());

        for (UnprocessedTransaction record : batch) {
            processRecord(record);
        }
    }

    private void processRecord(UnprocessedTransaction record) {
        String pair = record.getCurrencyShortname().code() + "/USD";
        LocalDate date = record.getDateTime().toLocalDate();

        boolean success = false;
        String lastError = null;

        for (int attempt = 1; attempt <= properties.retriesPerAttempt(); attempt++) {
            try {
                Optional<ExchangeRate> rateOpt = currencyRateApiClient.fetchRate(pair, date);
                if (rateOpt.isPresent()) {
                    exchangeRateRepository.saveOrUpdate(rateOpt.get());
                    success = true;
                    log.info("Worker successfully obtained rate for record ID {} (pair {}) on attempt {}/{}",
                            record.getId(), pair, attempt, properties.retriesPerAttempt());
                    break;
                } else {
                    lastError = "Rate not returned by external API for " + pair;
                }
            } catch (Exception ex) {
                lastError = ex.getMessage();
                log.warn("Attempt {}/{} failed for record ID {}: {}",
                        attempt, properties.retriesPerAttempt(), record.getId(), ex.getMessage());
            }

            if (attempt < properties.retriesPerAttempt() && properties.retryBackoff().toMillis() > 0) {
                try {
                    Thread.sleep(properties.retryBackoff().toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (success) {
            var handler = recoveryHandlerProvider.getIfAvailable();
            if (handler != null) {
                handler.completeRecoveredTransaction(record);
            }
            record.markProcessed();
            repository.update(record);
            log.info("Unprocessed transaction ID {} successfully processed and completed", record.getId());
        } else {
            int newRetryCount = record.getRetryCount() + 1;
            if (newRetryCount >= properties.retriesTotal()) {
                record.markFailed(lastError != null ? lastError : "Exceeded total retries limit");
                log.error("Unprocessed transaction ID {} exceeded retriesTotal ({}), marked as FAILED",
                        record.getId(), properties.retriesTotal());
            } else {
                record.incrementRetry(lastError != null ? lastError : "Retry failed");
                log.warn("Unprocessed transaction ID {} failed this round, incremented retryCount to {}",
                        record.getId(), record.getRetryCount());
            }
            repository.update(record);
        }
    }
}
