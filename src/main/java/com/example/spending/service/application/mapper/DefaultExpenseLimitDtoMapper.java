package com.example.spending.service.application.mapper;

import com.example.spending.service.application.port.in.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.application.port.in.dto.ExpenseLimitResponseDto;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.ExpenseLimit;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Default implementation of ExpenseLimitDtoMapper mapping DTOs to domain entities and vice versa.
 */
@Component
public class DefaultExpenseLimitDtoMapper implements ExpenseLimitDtoMapper {

    @Override
    public ExpenseLimit toEntity(CreateExpenseLimitRequestDto dto, OffsetDateTime limitDateTime) {
        if (dto == null) {
            return null;
        }
        return ExpenseLimit.builder()
                .accountFrom(AccountNumber.of(dto.accountFrom()))
                .expenseCategory(ExpenseCategory.fromCode(dto.expenseCategory()))
                .limitSum(dto.limitSum())
                .limitCurrency(CurrencyCode.USD)
                .limitDateTime(limitDateTime)
                .build();
    }

    @Override
    public ExpenseLimitResponseDto toDto(ExpenseLimit entity) {
        if (entity == null) {
            return null;
        }
        return new ExpenseLimitResponseDto(
                entity.getId(),
                entity.getAccountFrom() != null ? entity.getAccountFrom().value() : null,
                entity.getExpenseCategory() != null ? entity.getExpenseCategory().getCode() : null,
                entity.getLimitSum(),
                entity.getLimitCurrency() != null ? entity.getLimitCurrency().code() : "USD",
                entity.getLimitDateTime()
        );
    }

    @Override
    public List<ExpenseLimitResponseDto> toDtoList(List<ExpenseLimit> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toDto).toList();
    }
}
