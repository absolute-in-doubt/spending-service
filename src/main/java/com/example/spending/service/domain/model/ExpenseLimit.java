package com.example.spending.service.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExpenseLimit {
    private Long id;
    private AccountNumber accountFrom;
    private ExpenseCategory expenseCategory;
    private BigDecimal limitSum;
    private CurrencyCode limitCurrency;
    private OffsetDateTime limitDateTime;
    private OffsetDateTime createdAt;

    public static ExpenseLimit createDefault(AccountNumber accountFrom, ExpenseCategory category, OffsetDateTime effectiveFrom) {
        return ExpenseLimit.builder()
                .accountFrom(accountFrom)
                .expenseCategory(category)
                .limitSum(new BigDecimal("1000.00"))
                .limitCurrency(CurrencyCode.USD)
                .limitDateTime(effectiveFrom)
                .build();
    }
}
