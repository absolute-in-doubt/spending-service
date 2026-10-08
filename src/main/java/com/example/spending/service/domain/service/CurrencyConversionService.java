package com.example.spending.service.domain.service;

import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExchangeRate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Domain Service responsible for converting amounts to USD based on exchange rates.
 */
@Service
public class CurrencyConversionService {

    /**
     * Converts the given sum to USD using the provided exchange rate.
     * If currency is already USD, returns the sum directly.
     */
    public BigDecimal convertToUsd(BigDecimal amount, CurrencyCode currency, ExchangeRate rate) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Objects.requireNonNull(currency, "Currency must not be null");

        if (CurrencyCode.USD.equals(currency)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        Objects.requireNonNull(rate, "Exchange rate is required for converting " + currency + " to USD");
        BigDecimal effectiveRate = rate.getCloseRate();
        if (effectiveRate == null && rate.getPreviousCloseRate() != null) {
            effectiveRate = rate.getPreviousCloseRate();
        }
        if (effectiveRate == null) {
            throw new IllegalStateException("No close rate or previous close rate available for " + currency);
        }

        return amount.multiply(effectiveRate).setScale(2, RoundingMode.HALF_UP);
    }
}
