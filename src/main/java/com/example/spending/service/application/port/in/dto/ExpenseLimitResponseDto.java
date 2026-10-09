package com.example.spending.service.application.port.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ExpenseLimitResponseDto(
        Long id,
        @JsonProperty("account_from") String accountFrom,
        @JsonProperty("expense_category") String expenseCategory,
        @JsonProperty("limit_sum") BigDecimal limitSum,
        @JsonProperty("limit_currency_shortname") String limitCurrencyShortname,
        @JsonProperty("limit_datetime") OffsetDateTime limitDateTime
) {
}
