package com.example.spending.service.infrastructure.adapter.in.rest.impl;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.RegisterTransactionRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import com.example.spending.service.application.port.in.TransactionOperationService;
import com.example.spending.service.infrastructure.adapter.in.rest.TransactionController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller implementing TransactionController inbound port.
 * Handles transaction ingestion requests from banking integration systems.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class TransactionControllerImpl implements TransactionController {

    private final TransactionOperationService transactionOperationService;

    @Override
    public ResponseEntity<TransactionResponseDto> receiveTransaction(RegisterTransactionRequestDto request) {
        log.info("Received transaction request: account_from={}, account_to={}, sum={} {}, category={}, datetime={}",
                request.accountFrom(), request.accountTo(), request.sum(), request.currencyShortname(),
                request.expenseCategory(), request.dateTime());

        TransactionResponseDto response = transactionOperationService.saveTransactionOperation(request);
        return ResponseEntity.ok(response);
    }
}
