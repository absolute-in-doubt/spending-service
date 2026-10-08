package com.example.spending.service.domain.model;

import java.util.Arrays;

public enum ExpenseCategory {
    PRODUCT("product"),
    SERVICE("service");

    private final String code;

    ExpenseCategory(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static ExpenseCategory fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Expense category must not be null");
        }
        return Arrays.stream(values())
                .filter(c -> c.code.equalsIgnoreCase(code) || c.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown expense category: " + code));
    }
}
