package com.example.spending.service.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreateExpenseLimitRequestDto(
        @NotBlank(message = "account_from must not be blank")
        @Pattern(regexp = "^\\d{10}$", message = "account_from must be a 10-digit account number")
        @JsonProperty("account_from") String accountFrom,

        @NotBlank(message = "expense_category must not be blank")
        @JsonProperty("expense_category") String expenseCategory,

        @NotNull(message = "limit_sum must not be null")
        @DecimalMin(value = "0.01", message = "limit_sum must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "limit_sum must have at most 2 decimal places")
        @JsonProperty("limit_sum") BigDecimal limitSum,

        @JsonProperty("limit_currency_shortname") String limitCurrencyShortname
) {
}
