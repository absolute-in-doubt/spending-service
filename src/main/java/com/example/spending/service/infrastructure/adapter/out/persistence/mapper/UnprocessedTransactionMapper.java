package com.example.spending.service.infrastructure.adapter.out.persistence.mapper;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.UnprocessedTransaction;
import com.example.spending.service.domain.model.UnprocessedTransactionStatus;
import com.example.spending.service.infrastructure.jooq.tables.records.UnprocessedTransactionsRecord;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Component;

@Component
public class UnprocessedTransactionMapper implements RecordMapper<UnprocessedTransactionsRecord, UnprocessedTransaction> {

    @Override
    public UnprocessedTransaction map(UnprocessedTransactionsRecord record) {
        if (record == null) {
            return null;
        }

        UnprocessedTransactionStatus status = null;
        if (record.getStatus() != null) {
            try {
                status = UnprocessedTransactionStatus.valueOf(record.getStatus());
            } catch (IllegalArgumentException ignored) {
                status = UnprocessedTransactionStatus.PENDING;
            }
        }

        return UnprocessedTransaction.builder()
                .id(record.getId())
                .accountFrom(AccountNumber.of(record.getAccountFrom()))
                .accountTo(AccountNumber.of(record.getAccountTo()))
                .currencyShortname(CurrencyCode.of(record.getCurrencyShortname()))
                .sum(record.getSum())
                .expenseCategory(ExpenseCategory.fromCode(record.getExpenseCategory()))
                .dateTime(record.getDatetime())
                .failureReason(record.getFailureReason())
                .status(status)
                .retryCount(record.getRetryCount() != null ? record.getRetryCount() : 0)
                .leaseToken(record.getLeaseToken())
                .leasedUntil(record.getLeasedUntil())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
