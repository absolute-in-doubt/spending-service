package com.example.spending.service.application.port.in.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegisterTransactionRequestDto(
        @NotBlank(message = "account_from must not be blank")
        @Pattern(regexp = "^\\d{10}$", message = "account_from must be a 10-digit account number")
        @JsonProperty("account_from") String accountFrom,

        @NotBlank(message = "account_to must not be blank")
        @Pattern(regexp = "^\\d{10}$", message = "account_to must be a 10-digit account number")
        @JsonProperty("account_to") String accountTo,

        @NotBlank(message = "currency_shortname must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "currency_shortname must be a 3-letter uppercase ISO code")
        @JsonProperty("currency_shortname") String currencyShortname,

        @NotNull(message = "sum must not be null")
        @DecimalMin(value = "0.0", message = "sum must be greater than zero")
        @Digits(integer = 17, fraction = 6, message = "sum must have at most 6 decimal places")
        @JsonProperty("sum") BigDecimal sum,

        @NotBlank(message = "expense_category must not be blank")
        @JsonProperty("expense_category") String expenseCategory,

        @NotNull(message = "datetime must not be null")
        @JsonProperty("datetime") OffsetDateTime dateTime
) {
}
