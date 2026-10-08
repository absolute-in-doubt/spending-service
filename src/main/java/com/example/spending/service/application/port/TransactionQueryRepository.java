package com.example.spending.service.application.port;

import com.example.spending.service.application.dto.ExceededTransactionResponseDto;
import com.example.spending.service.domain.model.AccountNumber;

import java.util.List;

public interface TransactionQueryRepository {

    /**
     * Finds all transactions that exceeded the limit for a given client account,
     * joined with the active limit details at the time of each transaction (Requirement 6).
     */
    List<ExceededTransactionResponseDto> findExceededTransactionsWithLimit(AccountNumber accountFrom);
}
