package com.example.spending.service.application.port.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponseDto(
        Long id,
        @JsonProperty("account_from") String accountFrom,
        @JsonProperty("account_to") String accountTo,
        @JsonProperty("currency_shortname") String currencyShortname,
        @JsonProperty("sum") BigDecimal sum,
        @JsonProperty("expense_category") String expenseCategory,
        @JsonProperty("datetime") OffsetDateTime dateTime,
        @JsonProperty("limit_exceeded") boolean limitExceeded
) {
}
