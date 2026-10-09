package com.example.spending.service.infrastructure.worker;

import com.example.spending.service.domain.model.UnprocessedTransaction;

/**
 * Handler interface for finalizing a recovered transaction once its currency rate is obtained.
 */
public interface TransactionRecoveryHandler {
    void completeRecoveredTransaction(UnprocessedTransaction record);
}
