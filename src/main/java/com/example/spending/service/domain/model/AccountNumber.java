package com.example.spending.service.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record AccountNumber(String value) {
    private static final Pattern PATTERN = Pattern.compile("^\\d{10}$");

    public AccountNumber {
        Objects.requireNonNull(value, "Account number must not be null");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Account number must consist of exactly 10 digits: " + value);
        }
    }

    public static AccountNumber of(String value) {
        return new AccountNumber(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
