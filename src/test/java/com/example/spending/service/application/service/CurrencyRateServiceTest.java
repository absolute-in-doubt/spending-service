package com.example.spending.service.application.service;

import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.application.service.impl.CurrencyRateServiceImpl;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceTest {

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyRateApiClient currencyRateApiClient;

    private CurrencyRateServiceImpl service;

    private final LocalDate testDate = LocalDate.of(2026, 10, 9);
    private final String pair = "KZT/USD";
    private final String currencyFrom = "KZT";
    private final String currencyTo = "USD";

    @BeforeEach
    void setUp() {
        service = new CurrencyRateServiceImpl(exchangeRateRepository, currencyRateApiClient);
    }

    @Test
    @DisplayName("Should return cached exchange rate from DB without calling external API")
    void shouldReturnCachedRateWhenPresentInDb() {
        ExchangeRate cached = ExchangeRate.builder()
                .currencyPair(currencyFrom + "/" + currencyTo)
                .rateDate(testDate)
                .closeRate(new BigDecimal("0.0021"))
                .previousCloseRate(new BigDecimal("0.0020"))
                .build();

        when(exchangeRateRepository.findByPairAndDate(pair, testDate)).thenReturn(Optional.of(cached));

        Optional<ExchangeRate> result = service.getOrFetchRate(pair, testDate);

        assertThat(result).isPresent().contains(cached);
        verify(exchangeRateRepository).findByPairAndDate(pair, testDate);
        verifyNoInteractions(currencyRateApiClient);
        verify(exchangeRateRepository, never()).saveOrUpdate(any());
    }

    @Test
    @DisplayName("Should fetch rate from external API and save to DB when cache misses")
    void shouldFetchFromApiAndSaveToDbWhenNotInDb() {
        ExchangeRate fetched = ExchangeRate.builder()
                .currencyPair(pair)
                .rateDate(testDate)
                .closeRate(new BigDecimal("0.0021"))
                .previousCloseRate(new BigDecimal("0.0020"))
                .build();

        when(exchangeRateRepository.findByPairAndDate(pair, testDate)).thenReturn(Optional.empty());
        when(currencyRateApiClient.fetchRate(currencyFrom, currencyTo, testDate)).thenReturn(Optional.of(fetched));

        Optional<ExchangeRate> result = service.getOrFetchRate(pair, testDate);

        assertThat(result).isPresent().contains(fetched);
        verify(exchangeRateRepository).saveOrUpdate(fetched);
        verify(exchangeRateRepository, never()).findEffectiveRate(any(), any());
    }

    @Test
    @DisplayName("Should fallback to effective rate in DB when external API returns empty (e.g. weekend/holiday)")
    void shouldFallbackToEffectiveRateWhenApiReturnsEmpty() {
        ExchangeRate historicalRate = ExchangeRate.builder()
                .currencyPair(pair)
                .rateDate(testDate.minusDays(1))
                .closeRate(new BigDecimal("0.0020"))
                .build();

        when(exchangeRateRepository.findByPairAndDate(pair, testDate)).thenReturn(Optional.empty());
        when(currencyRateApiClient.fetchRate(currencyFrom, currencyTo, testDate)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findEffectiveRate(pair, testDate)).thenReturn(Optional.of(historicalRate));

        Optional<ExchangeRate> result = service.getOrFetchRate(pair, testDate);

        assertThat(result).isPresent().contains(historicalRate);
        verify(exchangeRateRepository).findEffectiveRate(pair, testDate);
        verify(exchangeRateRepository, never()).saveOrUpdate(any());
    }

    @Test
    @DisplayName("Should fallback to effective rate in DB when external API throws exception")
    void shouldFallbackToEffectiveRateWhenApiThrowsException() {
        ExchangeRate historicalRate = ExchangeRate.builder()
                .currencyPair(pair)
                .rateDate(testDate.minusDays(1))
                .closeRate(new BigDecimal("0.0020"))
                .build();

        when(exchangeRateRepository.findByPairAndDate(pair, testDate)).thenReturn(Optional.empty());
        when(currencyRateApiClient.fetchRate(currencyFrom, currencyTo, testDate)).thenThrow(new RuntimeException("Third-party service down"));
        when(exchangeRateRepository.findEffectiveRate(pair, testDate)).thenReturn(Optional.of(historicalRate));

        Optional<ExchangeRate> result = service.getOrFetchRate(pair, testDate);

        assertThat(result).isPresent().contains(historicalRate);
        verify(exchangeRateRepository).findEffectiveRate(pair, testDate);
    }

    @Test
    @DisplayName("Should return empty when rate is not in DB, API returns empty, and no effective rate in DB")
    void shouldReturnEmptyWhenBothApiAndEffectiveRateUnavailable() {
        when(exchangeRateRepository.findByPairAndDate(pair, testDate)).thenReturn(Optional.empty());
        when(currencyRateApiClient.fetchRate(currencyFrom, currencyTo, testDate)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findEffectiveRate(pair, testDate)).thenReturn(Optional.empty());

        Optional<ExchangeRate> result = service.getOrFetchRate(pair, testDate);

        assertThat(result).isEmpty();
    }
}
