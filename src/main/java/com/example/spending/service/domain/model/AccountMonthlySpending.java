package com.example.spending.service.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Domain entity representing the accumulated monthly spending aggregate for an account and category.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AccountMonthlySpending {

    private AccountNumber accountFrom;
    private ExpenseCategory expenseCategory;
    private YearMonth yearMonth;
    private BigDecimal totalSpentUsd;
    private OffsetDateTime updatedAt;

    public static AccountMonthlySpending createDefault(AccountNumber accountFrom, ExpenseCategory expenseCategory, YearMonth yearMonth) {
        return AccountMonthlySpending.builder()
                .accountFrom(accountFrom)
                .expenseCategory(expenseCategory)
                .yearMonth(yearMonth)
                .totalSpentUsd(BigDecimal.ZERO)
                .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    public void addExpense(BigDecimal amountUsd) {
        Objects.requireNonNull(amountUsd, "Expense amount must not be null");
        BigDecimal current = this.totalSpentUsd != null ? this.totalSpentUsd : BigDecimal.ZERO;
        this.totalSpentUsd = current.add(amountUsd);
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
