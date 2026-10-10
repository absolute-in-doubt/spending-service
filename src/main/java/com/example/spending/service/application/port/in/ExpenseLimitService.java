package com.example.spending.service.application.port.in;

import com.example.spending.service.infrastructure.adapter.in.rest.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.infrastructure.adapter.in.rest.dto.ExpenseLimitResponseDto;

import java.util.List;

/**
 * Inbound port for managing client expense limits.
 */
public interface ExpenseLimitService {

    /**
     * Creates and persists a new expense limit for the specified account and category.
     *
     * @param request the limit creation DTO
     * @return the saved ExpenseLimitResponseDto
     */
    ExpenseLimitResponseDto createLimit(CreateExpenseLimitRequestDto request);

    /**
     * Retrieves all historical expense limits established for the given client account number.
     *
     * @param accountFrom the 10-digit account number string
     * @return list of ExpenseLimitResponseDto ordered chronologically descending
     */
    List<ExpenseLimitResponseDto> getLimits(String accountFrom);
}
