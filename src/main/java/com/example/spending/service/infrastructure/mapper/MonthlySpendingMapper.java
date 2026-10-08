package com.example.spending.service.infrastructure.mapper;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.AccountMonthlySpending;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.infrastructure.jooq.tables.records.AccountMonthlySpendingRecord;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

@Component
public class MonthlySpendingMapper implements RecordMapper<AccountMonthlySpendingRecord, AccountMonthlySpending> {

    @Override
    public AccountMonthlySpending map(AccountMonthlySpendingRecord record) {
        if (record == null) {
            return null;
        }
        return AccountMonthlySpending.builder()
                .accountFrom(AccountNumber.of(record.getAccountFrom()))
                .expenseCategory(ExpenseCategory.fromCode(record.getExpenseCategory()))
                .yearMonth(YearMonth.parse(record.getYearMonth()))
                .totalSpentUsd(record.getTotalSpentUsd())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
