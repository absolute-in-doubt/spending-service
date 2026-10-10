package com.example.spending.service.infrastructure.adapter.in.rest.mapper;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.RegisterTransactionRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.TransactionResponseDto;
import com.example.spending.service.domain.model.TransactionOperation;

/**
 * Mapper interface for converting between transaction DTOs and domain entities.
 */
public interface TransactionOperationDtoMapper {

    TransactionOperation toEntity(RegisterTransactionRequestDto dto, boolean limitExceeded);

    TransactionResponseDto toDto(TransactionOperation entity);
}
