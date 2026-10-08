package com.example.spending.service.domain.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.TransactionOperation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionOperationRepository {

    TransactionOperation save(TransactionOperation transaction);

    Optional<TransactionOperation> findById(Long id);

    void lockAccount(AccountNumber accountFrom);

    BigDecimal sumMonthlyExpensesInUsd(AccountNumber accountFrom, ExpenseCategory category, OffsetDateTime from, OffsetDateTime to);

    List<TransactionOperation> findExceededTransactions(AccountNumber accountFrom);
}
