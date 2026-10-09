package com.example.spending.service.domain.service;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.ExpenseLimit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LimitCalculationServiceTest {

    private final LimitCalculationService service = new LimitCalculationService();

    private final AccountNumber account = AccountNumber.of("0000000123");
    private final OffsetDateTime now = OffsetDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);

    @Test
    @DisplayName("Should use default 1000 USD limit when active limit is null")
    void shouldUseDefaultLimitWhenNull() {
        // 0 + 500 = 500 <= 1000
        assertThat(service.isLimitExceeded(null, BigDecimal.ZERO, new BigDecimal("500.00"))).isFalse();

        // 900 + 100 = 1000 <= 1000 (equal is not exceeded)
        assertThat(service.isLimitExceeded(null, new BigDecimal("900.00"), new BigDecimal("100.00"))).isFalse();

        // 900 + 100.01 = 1000.01 > 1000 (exceeded)
        assertThat(service.isLimitExceeded(null, new BigDecimal("900.00"), new BigDecimal("100.01"))).isTrue();
    }

    @Test
    @DisplayName("Should respect custom active limit")
    void shouldRespectCustomActiveLimit() {
        ExpenseLimit customLimit = ExpenseLimit.builder()
                .id(1L)
                .accountFrom(account)
                .expenseCategory(ExpenseCategory.PRODUCT)
                .limitSum(new BigDecimal("500.00"))
                .limitCurrency(CurrencyCode.USD)
                .limitDateTime(now)
                .build();

        // 300 + 200 = 500 <= 500
        assertThat(service.isLimitExceeded(customLimit, new BigDecimal("300.00"), new BigDecimal("200.00"))).isFalse();

        // 300 + 201 = 501 > 500
        assertThat(service.isLimitExceeded(customLimit, new BigDecimal("300.00"), new BigDecimal("201.00"))).isTrue();
    }

    @Test
    @DisplayName("Should handle null previous spent as zero")
    void shouldHandleNullPreviousSpentAsZero() {
        assertThat(service.isLimitExceeded(null, null, new BigDecimal("1000.00"))).isFalse();
        assertThat(service.isLimitExceeded(null, null, new BigDecimal("1000.01"))).isTrue();
    }
}
