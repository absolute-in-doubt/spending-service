package com.example.spending.service.infrastructure.adapter.out.persistence.mapper;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.ExpenseLimit;
import com.example.spending.service.infrastructure.jooq.tables.records.ExpenseLimitsRecord;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Component;

@Component
public class ExpenseLimitMapper implements RecordMapper<ExpenseLimitsRecord, ExpenseLimit> {

    @Override
    public ExpenseLimit map(ExpenseLimitsRecord record) {
        if (record == null) {
            return null;
        }
        return ExpenseLimit.builder()
                .id(record.getId())
                .accountFrom(AccountNumber.of(record.getAccountFrom()))
                .expenseCategory(ExpenseCategory.fromCode(record.getExpenseCategory()))
                .limitSum(record.getLimitSum())
                .limitCurrency(CurrencyCode.of(record.getLimitCurrencyShortname()))
                .limitDateTime(record.getLimitDatetime())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
