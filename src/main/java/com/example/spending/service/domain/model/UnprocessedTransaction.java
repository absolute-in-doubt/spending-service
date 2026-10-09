package com.example.spending.service.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Domain entity representing an incoming transaction that could not be processed immediately
 * (e.g. due to missing exchange rate) and is parked for retry or manual investigation (DLQ pattern)
 * with lease-based distributed locking support.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UnprocessedTransaction {

    private Long id;
    private AccountNumber accountFrom;
    private AccountNumber accountTo;
    private CurrencyCode currencyShortname;
    private BigDecimal sum;
    private ExpenseCategory expenseCategory;
    private OffsetDateTime dateTime;
    private String failureReason;
    private UnprocessedTransactionStatus status;
    private int retryCount;
    private String leaseToken;
    private OffsetDateTime leasedUntil;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static UnprocessedTransaction create(
            AccountNumber accountFrom,
            AccountNumber accountTo,
            CurrencyCode currencyShortname,
            BigDecimal sum,
            ExpenseCategory expenseCategory,
            OffsetDateTime dateTime,
            String failureReason
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return UnprocessedTransaction.builder()
                .accountFrom(accountFrom)
                .accountTo(accountTo)
                .currencyShortname(currencyShortname)
                .sum(sum)
                .expenseCategory(expenseCategory)
                .dateTime(dateTime)
                .failureReason(failureReason)
                .status(UnprocessedTransactionStatus.PENDING)
                .retryCount(0)
                .leaseToken(null)
                .leasedUntil(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public void lease(String workerToken, OffsetDateTime until) {
        this.leaseToken = workerToken;
        this.leasedUntil = until;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void releaseLease() {
        this.leaseToken = null;
        this.leasedUntil = null;
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public boolean isLeaseExpired(OffsetDateTime now) {
        return this.leasedUntil == null || this.leasedUntil.isBefore(now);
    }

    public void incrementRetry(String reason) {
        this.retryCount++;
        this.failureReason = reason;
        this.releaseLease();
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markProcessed() {
        this.status = UnprocessedTransactionStatus.PROCESSED;
        this.releaseLease();
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markFailed(String reason) {
        this.status = UnprocessedTransactionStatus.FAILED;
        this.failureReason = reason;
        this.releaseLease();
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
