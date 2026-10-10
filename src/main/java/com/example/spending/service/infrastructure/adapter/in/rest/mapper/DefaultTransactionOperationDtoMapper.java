package com.example.spending.service.infrastructure.adapter.in.rest.mapper;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.RegisterTransactionRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.TransactionOperation;
import org.springframework.stereotype.Component;

/**
 * Default implementation of TransactionOperationDtoMapper that handles conversion between
 * DTOs and domain types (AccountNumber, CurrencyCode, ExpenseCategory).
 */
@Component
public class DefaultTransactionOperationDtoMapper implements TransactionOperationDtoMapper {

    @Override
    public TransactionOperation toEntity(RegisterTransactionRequestDto dto, boolean limitExceeded) {
        if (dto == null) {
            return null;
        }
        return TransactionOperation.create(
                AccountNumber.of(dto.accountFrom()),
                AccountNumber.of(dto.accountTo()),
                CurrencyCode.of(dto.currencyShortname()),
                dto.sum(),
                ExpenseCategory.fromCode(dto.expenseCategory()),
                dto.dateTime(),
                limitExceeded
        );
    }

    @Override
    public TransactionResponseDto toDto(TransactionOperation entity) {
        if (entity == null) {
            return null;
        }
        return new TransactionResponseDto(
                entity.getId(),
                entity.getAccountFrom() != null ? entity.getAccountFrom().value() : null,
                entity.getAccountTo() != null ? entity.getAccountTo().value() : null,
                entity.getCurrencyShortname() != null ? entity.getCurrencyShortname().code() : null,
                entity.getSum(),
                entity.getExpenseCategory() != null ? entity.getExpenseCategory().getCode() : null,
                entity.getDateTime(),
                entity.isLimitExceeded()
        );
    }
}
