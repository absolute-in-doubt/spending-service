package com.example.spending.service.domain.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.UnprocessedTransaction;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface UnprocessedTransactionRepository {

    /**
     * Saves a new unprocessed transaction in the DLQ.
     */
    UnprocessedTransaction save(UnprocessedTransaction transaction);

    /**
     * Finds an unprocessed transaction by its ID.
     */
    Optional<UnprocessedTransaction> findById(Long id);

    /**
     * Atomically acquires a batch of pending unprocessed transactions for retry by setting the lease token and expiry.
     * Selects records where status = PENDING, retry_count < maxRetries, and (leased_until IS NULL OR leased_until < now).
     */
    List<UnprocessedTransaction> acquireBatchForRetry(String workerToken, OffsetDateTime now, OffsetDateTime leaseUntil, int maxRetries, int limit);

    /**
     * Updates an existing unprocessed transaction (e.g. after a retry attempt, status change, or lease release).
     */
    void update(UnprocessedTransaction transaction);

    /**
     * Checks if there are any unresolved (PENDING or FAILED) unprocessed transactions
     * for the given account, category, and month.
     */
    boolean hasUnresolvedTransactions(AccountNumber accountFrom, ExpenseCategory category, YearMonth yearMonth);
}
