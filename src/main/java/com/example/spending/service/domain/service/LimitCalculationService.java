package com.example.spending.service.domain.service;

import com.example.spending.service.domain.model.ExpenseLimit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Domain Service responsible for calculating whether a transaction exceeds the monthly spending limit.
 */
@Service
public class LimitCalculationService {

    public static final BigDecimal DEFAULT_LIMIT_USD = new BigDecimal("1000.00");

    /**
     * Checks whether the current transaction causes total monthly expenses to exceed the active limit.
     *
     * @param activeLimit the active limit (or null if none set, in which case default 1000 USD is used)
     * @param spentBeforeTransactionUsd accumulated expenses in current month before this transaction
     * @param transactionAmountUsd amount of current transaction converted to USD
     * @return true if new total monthly expenses exceed the limit, false otherwise
     */
    public boolean isLimitExceeded(
            ExpenseLimit activeLimit,
            BigDecimal spentBeforeTransactionUsd,
            BigDecimal transactionAmountUsd
    ) {
        Objects.requireNonNull(transactionAmountUsd, "transactionAmountUsd must not be null");

        BigDecimal effectiveLimit = (activeLimit != null && activeLimit.getLimitSum() != null)
                ? activeLimit.getLimitSum()
                : DEFAULT_LIMIT_USD;

        BigDecimal previousSpent = (spentBeforeTransactionUsd != null)
                ? spentBeforeTransactionUsd
                : BigDecimal.ZERO;

        BigDecimal newTotal = previousSpent.add(transactionAmountUsd);
        return newTotal.compareTo(effectiveLimit) > 0;
    }
}
