package com.example.spending.service.infrastructure.worker;

import com.example.spending.service.application.port.in.CurrencyRateService;
import com.example.spending.service.infrastructure.adapter.in.worker.CurrencyRateSyncWorker;
import com.example.spending.service.infrastructure.adapter.in.worker.config.CurrencyRateSyncProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CurrencyRateSyncWorkerTest {

    @Mock
    private CurrencyRateService currencyRateService;

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
        worker = new CurrencyRateSyncWorker(currencyRateService, properties, fixedClock);
    }

    @Test
    @DisplayName("Should call CurrencyRateService for all configured default currency pairs")
    void shouldCallCurrencyRateServiceForConfiguredPairs() {
        worker.syncDefaultRates();

        verify(currencyRateService).getOrFetchRate("KZT/USD", testDate);
        verify(currencyRateService).getOrFetchRate("RUB/USD", testDate);
        verifyNoMoreInteractions(currencyRateService);
    }

    @Test
    @DisplayName("Should trigger sync on startup when enabled and runOnStartup is true")
    void shouldTriggerSyncOnStartupWhenEnabled() {
        worker.onStartup();

        verify(currencyRateService).getOrFetchRate("KZT/USD", testDate);
        verify(currencyRateService).getOrFetchRate("RUB/USD", testDate);
    }

    @Test
    @DisplayName("Should skip startup sync when disabled")
    void shouldSkipStartupSyncWhenDisabled() {
        CurrencyRateSyncProperties disabledProps = new CurrencyRateSyncProperties(
                false,
                Duration.ofHours(1),
                true,
                List.of("KZT/USD")
        );
        CurrencyRateSyncWorker disabledWorker = new CurrencyRateSyncWorker(currencyRateService, disabledProps, fixedClock);

        disabledWorker.onStartup();

        verifyNoInteractions(currencyRateService);
    }

    @Test
    @DisplayName("Should continue processing subsequent pairs when one pair throws exception")
    void shouldHandleExceptionsDuringPairSyncGracefully() {
        when(currencyRateService.getOrFetchRate("KZT/USD", testDate))
                .thenThrow(new RuntimeException("API connection timeout"));

        worker.syncDefaultRates();

        verify(currencyRateService).getOrFetchRate("KZT/USD", testDate);
        verify(currencyRateService).getOrFetchRate("RUB/USD", testDate);
    }
}
