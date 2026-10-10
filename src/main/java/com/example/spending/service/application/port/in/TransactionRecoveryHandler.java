package com.example.spending.service.application.port.in;

import com.example.spending.service.domain.model.UnprocessedTransaction;

/**
 * Inbound port for finalizing a recovered transaction once its currency rate is obtained.
 */
public interface TransactionRecoveryHandler {

    /**
     * Completes transaction execution after its exchange rate has been recovered.
     *
     * @param record the recovered unprocessed transaction entity
     */
    void completeRecoveredTransaction(UnprocessedTransaction record);
}
