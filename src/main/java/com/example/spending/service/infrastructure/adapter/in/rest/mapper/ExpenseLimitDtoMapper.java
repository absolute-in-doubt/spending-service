package com.example.spending.service.infrastructure.adapter.in.rest.mapper;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExpenseLimitResponseDto;
import com.example.spending.service.domain.model.ExpenseLimit;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Mapper interface for converting between expense limit DTOs and domain entities.
 */
public interface ExpenseLimitDtoMapper {

    ExpenseLimit toEntity(CreateExpenseLimitRequestDto dto, OffsetDateTime limitDateTime);

    ExpenseLimitResponseDto toDto(ExpenseLimit entity);

    List<ExpenseLimitResponseDto> toDtoList(List<ExpenseLimit> entities);
}
