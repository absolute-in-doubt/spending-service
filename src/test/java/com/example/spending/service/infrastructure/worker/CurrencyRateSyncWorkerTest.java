package com.example.spending.service.infrastructure.worker;

import com.example.spending.service.application.port.out.CurrencyRateApiClient;
import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import com.example.spending.service.infrastructure.worker.config.CurrencyRateSyncProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyRateSyncWorkerTest {

    @Mock
    private CurrencyRateApiClient currencyRateApiClient;

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    private Clock fixedClock;
    private CurrencyRateSyncProperties properties;
    private CurrencyRateSyncWorker worker;

    private final LocalDate testDate = LocalDate.of(2026, 10, 9);

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
        properties = new CurrencyRateSyncProperties(
                true,
                Duration.ofHours(1),
                true,
                List.of("KZT/USD", "RUB/USD")
        );
        worker = new CurrencyRateSyncWorker(currencyRateApiClient, exchangeRateRepository, properties, fixedClock);
    }

    @Test
    @DisplayName("Should skip external API call when exchange rate is already cached in database")
    void shouldSkipApiCallWhenRateAlreadyInDb() {
        ExchangeRate cachedRate = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(testDate)
                .closeRate(new BigDecimal("0.0021"))
                .build();

        when(exchangeRateRepository.findByPairAndDate("KZT/USD", testDate)).thenReturn(Optional.of(cachedRate));
        when(exchangeRateRepository.findByPairAndDate("RUB/USD", testDate)).thenReturn(Optional.of(cachedRate));

        worker.syncDefaultRates();

        verify(exchangeRateRepository).findByPairAndDate("KZT/USD", testDate);
        verify(exchangeRateRepository).findByPairAndDate("RUB/USD", testDate);
        verifyNoInteractions(currencyRateApiClient);
        verify(exchangeRateRepository, never()).saveOrUpdate(any());
    }

    @Test
    @DisplayName("Should fetch rate from external API and save to DB when rate is not cached")
    void shouldFetchAndSaveRateWhenNotCached() {
        ExchangeRate fetchedKzt = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(testDate)
                .closeRate(new BigDecimal("0.0021"))
                .previousCloseRate(new BigDecimal("0.0020"))
                .build();

        ExchangeRate fetchedRub = ExchangeRate.builder()
                .currencyPair("RUB/USD")
                .rateDate(testDate)
                .closeRate(new BigDecimal("0.0105"))
                .previousCloseRate(new BigDecimal("0.0104"))
                .build();

        when(exchangeRateRepository.findByPairAndDate("KZT/USD", testDate)).thenReturn(Optional.empty());
        when(exchangeRateRepository.findByPairAndDate("RUB/USD", testDate)).thenReturn(Optional.empty());

        when(currencyRateApiClient.fetchRate("KZT/USD", testDate)).thenReturn(Optional.of(fetchedKzt));
        when(currencyRateApiClient.fetchRate("RUB/USD", testDate)).thenReturn(Optional.of(fetchedRub));

        worker.syncDefaultRates();

        verify(currencyRateApiClient).fetchRate("KZT/USD", testDate);
        verify(currencyRateApiClient).fetchRate("RUB/USD", testDate);
        verify(exchangeRateRepository).saveOrUpdate(fetchedKzt);
        verify(exchangeRateRepository).saveOrUpdate(fetchedRub);
    }

    @Test
    @DisplayName("Should handle empty rate response gracefully without saving to DB")
    void shouldHandleEmptyResponseGracefully() {
        when(exchangeRateRepository.findByPairAndDate(any(), eq(testDate))).thenReturn(Optional.empty());
        when(currencyRateApiClient.fetchRate(any(), eq(testDate))).thenReturn(Optional.empty());

        worker.syncDefaultRates();

        verify(currencyRateApiClient, times(2)).fetchRate(any(), eq(testDate));
        verify(exchangeRateRepository, never()).saveOrUpdate(any());
    }
}
