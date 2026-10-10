package com.example.spending.service.infrastructure.adapter.in.rest.impl;

import com.example.spending.service.infrastructure.adapter.in.rest.ClientController;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExceededTransactionResponseDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExpenseLimitResponseDto;
import com.example.spending.service.application.port.in.ExpenseLimitService;
import com.example.spending.service.application.port.in.TransactionOperationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller implementing ClientController inbound port.
 * Handles client-facing requests: establishing new limits, retrieving limits,
 * and fetching transactions that exceeded the monthly limit.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ClientControllerImpl implements ClientController {

    private final ExpenseLimitService expenseLimitService;
    private final TransactionOperationService transactionOperationService;

    @Override
    public ResponseEntity<ExpenseLimitResponseDto> createLimit(CreateExpenseLimitRequestDto request) {
        log.info("Received create limit request: account_from={}, category={}, sum={} {}",
                request.accountFrom(), request.expenseCategory(), request.limitSum(), request.limitCurrencyShortname());

        ExpenseLimitResponseDto response = expenseLimitService.createLimit(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<List<ExpenseLimitResponseDto>> getLimits(
            @RequestParam(name = "account_from", required = false) String accountFrom
    ) {
        String account = validateAndResolveAccount(accountFrom);
        log.debug("Fetching expense limits for account: {}", account);

        List<ExpenseLimitResponseDto> limits = expenseLimitService.getLimits(account);
        return ResponseEntity.ok(limits);
    }

    @Override
    public ResponseEntity<List<ExceededTransactionResponseDto>> getExceededTransactions(
            @RequestParam(name = "account_from", required = false) String accountFrom
    ) {
        String account = validateAndResolveAccount(accountFrom);
        log.debug("Fetching exceeded transactions for account: {}", account);

        List<ExceededTransactionResponseDto> exceeded = transactionOperationService.getExceededTransactions(account);
        return ResponseEntity.ok(exceeded);
    }

    private String validateAndResolveAccount(String accountFrom) {
        if (accountFrom == null || accountFrom.trim().isBlank()) {
            throw new IllegalArgumentException("Query parameter 'account_from' is required");
        }
        return accountFrom.trim();
    }
}
