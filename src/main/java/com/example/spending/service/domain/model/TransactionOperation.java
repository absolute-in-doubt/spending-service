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
public class TransactionOperation {
    private Long id;
    private AccountNumber accountFrom;
    private AccountNumber accountTo;
    private CurrencyCode currencyShortname;
    private BigDecimal sum;
    private ExpenseCategory expenseCategory;
    private OffsetDateTime dateTime;
    private boolean limitExceeded;
    private Long version;

    public static TransactionOperation create(
            AccountNumber accountFrom,
            AccountNumber accountTo,
            CurrencyCode currencyShortname,
            BigDecimal sum,
            ExpenseCategory expenseCategory,
            OffsetDateTime dateTime,
            boolean limitExceeded
    ) {
        return TransactionOperation.builder()
                .accountFrom(accountFrom)
                .accountTo(accountTo)
                .currencyShortname(currencyShortname)
                .sum(sum)
                .expenseCategory(expenseCategory)
                .dateTime(dateTime)
                .limitExceeded(limitExceeded)
                .version(0L)
                .build();
    }
}
