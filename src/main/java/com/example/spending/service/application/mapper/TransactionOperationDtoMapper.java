package com.example.spending.service.application.mapper;

import com.example.spending.service.application.port.in.dto.RegisterTransactionRequestDto;
import com.example.spending.service.application.port.in.dto.TransactionResponseDto;
import com.example.spending.service.domain.model.TransactionOperation;

/**
 * Mapper interface for converting between transaction DTOs and domain entities.
 */
public interface TransactionOperationDtoMapper {

    TransactionOperation toEntity(RegisterTransactionRequestDto dto, boolean limitExceeded);

    TransactionResponseDto toDto(TransactionOperation entity);
}
