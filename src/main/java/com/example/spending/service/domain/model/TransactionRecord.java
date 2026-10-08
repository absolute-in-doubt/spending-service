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
public class TransactionRecord {
    private Long id;
    private AccountNumber accountFrom;
    private AccountNumber accountTo;
    private CurrencyCode currencyShortname;
    private BigDecimal sum;
    private ExpenseCategory expenseCategory;
    private OffsetDateTime dateTime;
    private boolean limitExceeded;
    private Long version;
}
