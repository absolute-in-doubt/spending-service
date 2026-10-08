package com.example.spending.service.domain.model;

import java.util.Objects;
import java.util.regex.Pattern;

public record CurrencyCode(String code) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Z]{3}$");

    public static final CurrencyCode USD = new CurrencyCode("USD");
    public static final CurrencyCode KZT = new CurrencyCode("KZT");
    public static final CurrencyCode RUB = new CurrencyCode("RUB");

    public CurrencyCode {
        Objects.requireNonNull(code, "Currency code must not be null");
        code = code.trim().toUpperCase();
        if (!PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("Currency code must be a 3-letter ISO code: " + code);
        }
    }

    public static CurrencyCode of(String code) {
        return new CurrencyCode(code);
    }

    @Override
    public String toString() {
        return code;
    }
}
