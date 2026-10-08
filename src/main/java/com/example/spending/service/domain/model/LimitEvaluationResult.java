package com.example.spending.service.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value Object representing the outcome of evaluating a transaction against a limit.
 */
public record LimitEvaluationResult(
        boolean limitExceeded,
        BigDecimal spentBeforeTransactionUsd,
        BigDecimal newTotalSpentUsd,
        BigDecimal limitSum,
        BigDecimal remainingLimitUsd
) {
    public LimitEvaluationResult {
        Objects.requireNonNull(spentBeforeTransactionUsd, "spentBeforeTransactionUsd must not be null");
        Objects.requireNonNull(newTotalSpentUsd, "newTotalSpentUsd must not be null");
        Objects.requireNonNull(limitSum, "limitSum must not be null");
        Objects.requireNonNull(remainingLimitUsd, "remainingLimitUsd must not be null");
    }

    public static LimitEvaluationResult evaluate(BigDecimal spentBeforeUsd, BigDecimal txAmountUsd, BigDecimal limitSum) {
        BigDecimal newTotal = spentBeforeUsd.add(txAmountUsd);
        boolean exceeded = newTotal.compareTo(limitSum) > 0;
        BigDecimal remaining = limitSum.subtract(newTotal);
        return new LimitEvaluationResult(exceeded, spentBeforeUsd, newTotal, limitSum, remaining);
    }
}
