package com.example.spending.service.domain.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.TransactionRecord;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRecordRepository {

    TransactionRecord save(TransactionRecord transaction);

    Optional<TransactionRecord> findById(Long id);

    void lockAccount(AccountNumber accountFrom);

    BigDecimal sumMonthlyExpensesInUsd(AccountNumber accountFrom, ExpenseCategory category, OffsetDateTime from, OffsetDateTime to);

    List<TransactionRecord> findExceededTransactions(AccountNumber accountFrom);
}
