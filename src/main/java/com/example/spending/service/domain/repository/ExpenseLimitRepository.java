package com.example.spending.service.domain.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.ExpenseLimit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ExpenseLimitRepository {

    ExpenseLimit save(ExpenseLimit limit);

    Optional<ExpenseLimit> findActiveLimit(AccountNumber accountFrom, ExpenseCategory category, OffsetDateTime dateTime);

    List<ExpenseLimit> findAllByAccount(AccountNumber accountFrom);
}
