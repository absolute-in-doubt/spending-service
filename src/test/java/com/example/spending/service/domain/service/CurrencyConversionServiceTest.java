package com.example.spending.service.domain.service;

import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyConversionServiceTest {

    private CurrencyConversionService conversionService;

    @BeforeEach
    void setUp() {
        conversionService = new CurrencyConversionService();
    }

    @Test
    @DisplayName("Should return amount directly when currency is USD")
    void shouldReturnAmountDirectlyWhenCurrencyIsUsd() {
        BigDecimal result = conversionService.convertToUsd(new BigDecimal("123.456"), CurrencyCode.USD, null);

        assertThat(result).isEqualByComparingTo("123.46");
    }

    @Test
    @DisplayName("Should convert using closeRate when present")
    void shouldConvertUsingCloseRateWhenPresent() {
        ExchangeRate rate = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(LocalDate.of(2026, 10, 9))
                .closeRate(new BigDecimal("0.0021"))
                .previousCloseRate(new BigDecimal("0.0020"))
                .build();

        BigDecimal result = conversionService.convertToUsd(new BigDecimal("1000"), CurrencyCode.KZT, rate);

        assertThat(result).isEqualByComparingTo("2.10");
    }

    @Test
    @DisplayName("Should fallback to previousCloseRate when closeRate is null (e.g. weekend or holiday)")
    void shouldFallbackToPreviousCloseRateWhenCloseRateIsNull() {
        ExchangeRate rate = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(LocalDate.of(2026, 10, 10))
                .closeRate(null)
                .previousCloseRate(new BigDecimal("0.0020"))
                .build();

        BigDecimal result = conversionService.convertToUsd(new BigDecimal("1000"), CurrencyCode.KZT, rate);

        assertThat(result).isEqualByComparingTo("2.00");
    }

    @Test
    @DisplayName("Should throw exception when both closeRate and previousCloseRate are null")
    void shouldThrowExceptionWhenBothRatesAreNull() {
        ExchangeRate rate = ExchangeRate.builder()
                .currencyPair("RUB/USD")
                .rateDate(LocalDate.of(2026, 10, 10))
                .closeRate(null)
                .previousCloseRate(null)
                .build();

        assertThatThrownBy(() -> conversionService.convertToUsd(new BigDecimal("100"), CurrencyCode.RUB, rate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No close rate or previous close rate available for RUB");
    }

    @Test
    @DisplayName("Should throw NullPointerException when amount or currency is null")
    void shouldThrowExceptionWhenParametersAreNull() {
        assertThatThrownBy(() -> conversionService.convertToUsd(null, CurrencyCode.USD, null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> conversionService.convertToUsd(BigDecimal.TEN, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
