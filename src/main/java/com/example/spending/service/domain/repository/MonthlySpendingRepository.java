package com.example.spending.service.domain.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.AccountMonthlySpending;
import com.example.spending.service.domain.model.ExpenseCategory;

import java.time.YearMonth;

public interface MonthlySpendingRepository {

    /**
     * Atomically ensures the monthly spending record exists for the given account, category, and month,
     * acquires a pessimistic row-level write lock (FOR UPDATE), and returns the AccountMonthlySpending entity.
     */
    AccountMonthlySpending getAndLock(AccountNumber accountFrom, ExpenseCategory category, YearMonth yearMonth);

    /**
     * Persists the updated monthly spending aggregate.
     */
    void save(AccountMonthlySpending spending);
}
