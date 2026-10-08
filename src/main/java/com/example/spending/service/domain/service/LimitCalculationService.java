package com.example.spending.service.domain.service;

import com.example.spending.service.domain.model.ExpenseLimit;
import com.example.spending.service.domain.model.LimitEvaluationResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Domain Service responsible for calculating monthly spending limit balances
 * and deciding whether a transaction exceeds the limit.
 */
@Service
public class LimitCalculationService {

    public static final BigDecimal DEFAULT_LIMIT_USD = new BigDecimal("1000.00");

    /**
     * Evaluates whether a new transaction exceeds the active limit.
     *
     * @param activeLimit the active limit (or null if none set, in which case default 1000 USD is used)
     * @param spentBeforeTransactionUsd accumulated expenses in current month before this transaction
     * @param transactionAmountUsd amount of current transaction converted to USD
     * @return LimitEvaluationResult containing limitExceeded flag, remaining balance, and amounts
     */
    public LimitEvaluationResult evaluateTransaction(
            ExpenseLimit activeLimit,
            BigDecimal spentBeforeTransactionUsd,
            BigDecimal transactionAmountUsd
    ) {
        BigDecimal effectiveLimit = (activeLimit != null && activeLimit.getLimitSum() != null)
                ? activeLimit.getLimitSum()
                : DEFAULT_LIMIT_USD;

        BigDecimal previousSpent = (spentBeforeTransactionUsd != null)
                ? spentBeforeTransactionUsd
                : BigDecimal.ZERO;

        return LimitEvaluationResult.evaluate(previousSpent, transactionAmountUsd, effectiveLimit);
    }
}
