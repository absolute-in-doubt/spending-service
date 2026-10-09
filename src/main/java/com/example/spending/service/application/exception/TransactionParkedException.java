package com.example.spending.service.application.exception;

import lombok.Getter;

/**
 * Exception thrown when a transaction cannot be processed immediately (e.g. missing exchange rate)
 * and is parked in the dead-letter queue (DLQ) for asynchronous retry.
 */
@Getter
public class TransactionParkedException extends RuntimeException {

    private final Long unprocessedTransactionId;

    public TransactionParkedException(String message, Long unprocessedTransactionId) {
        super(message);
        this.unprocessedTransactionId = unprocessedTransactionId;
    }
}
