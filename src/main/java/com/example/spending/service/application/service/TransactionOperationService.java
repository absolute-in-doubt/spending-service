package com.example.spending.service.application.service;

import com.example.spending.service.application.port.in.dto.ExceededTransactionResponseDto;
import com.example.spending.service.application.port.in.dto.RegisterTransactionRequestDto;
import com.example.spending.service.application.port.in.dto.TransactionResponseDto;

import java.util.List;

/**
 * Inbound port for client and bank transaction operations.
 */
public interface TransactionOperationService {

    /**
     * Receives and saves a client transaction operation according to business rules.
     *
     * @param request the incoming transaction request DTO
     * @return TransactionResponseDto containing transaction details and limit exceedance flag
     */
    TransactionResponseDto saveTransactionOperation(RegisterTransactionRequestDto request);

    /**
     * Retrieves all transactions that exceeded the monthly limit for a client account,
     * along with the active limit details at the time of each transaction (Requirement 6).
     *
     * @param accountFrom the 10-digit client account number
     * @return list of ExceededTransactionResponseDto
     */
    List<ExceededTransactionResponseDto> getExceededTransactions(String accountFrom);
}
