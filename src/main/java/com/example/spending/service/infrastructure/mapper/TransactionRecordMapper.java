package com.example.spending.service.infrastructure.mapper;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.TransactionRecord;
import com.example.spending.service.infrastructure.jooq.tables.records.TransactionRecordsRecord;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Component;

@Component
public class TransactionRecordMapper implements RecordMapper<TransactionRecordsRecord, TransactionRecord> {

    @Override
    public TransactionRecord map(TransactionRecordsRecord record) {
        if (record == null) {
            return null;
        }
        return TransactionRecord.builder()
                .id(record.getId())
                .accountFrom(AccountNumber.of(record.getAccountFrom()))
                .accountTo(AccountNumber.of(record.getAccountTo()))
                .currencyShortname(CurrencyCode.of(record.getCurrencyShortname()))
                .sum(record.getSum())
                .expenseCategory(ExpenseCategory.fromCode(record.getExpenseCategory()))
                .dateTime(record.getDatetime())
                .limitExceeded(Boolean.TRUE.equals(record.getLimitExceeded()))
                .version(record.getVersion())
                .build();
    }
}
