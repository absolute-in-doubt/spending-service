package com.example.spending.service.application.service;

import com.example.spending.service.application.exception.TransactionParkedException;
import com.example.spending.service.application.mapper.DefaultTransactionOperationDtoMapper;
import com.example.spending.service.application.mapper.TransactionOperationDtoMapper;
import com.example.spending.service.application.port.in.dto.RegisterTransactionRequestDto;
import com.example.spending.service.application.port.in.dto.TransactionResponseDto;
import com.example.spending.service.domain.model.*;
import com.example.spending.service.domain.repository.ExpenseLimitRepository;
import com.example.spending.service.domain.repository.MonthlySpendingRepository;
import com.example.spending.service.domain.repository.TransactionOperationRepository;
import com.example.spending.service.domain.repository.UnprocessedTransactionRepository;
import com.example.spending.service.domain.service.CurrencyConversionService;
import com.example.spending.service.domain.service.LimitCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionOperationServiceTest {

    @Mock
    private CurrencyRateService currencyRateService;

    @Mock
    private MonthlySpendingRepository monthlySpendingRepository;

    @Mock
    private ExpenseLimitRepository expenseLimitRepository;

    @Mock
    private TransactionOperationRepository transactionOperationRepository;

    @Mock
    private UnprocessedTransactionRepository unprocessedTransactionRepository;

    private final TransactionOperations transactionOperations = TransactionOperations.withoutTransaction();
    private final CurrencyConversionService currencyConversionService = new CurrencyConversionService();
    private final LimitCalculationService limitCalculationService = new LimitCalculationService();
    private final TransactionOperationDtoMapper mapper = new DefaultTransactionOperationDtoMapper();

    private TransactionOperationService service;

    private final OffsetDateTime now = OffsetDateTime.of(2026, 10, 9, 10, 0, 0, 0, ZoneOffset.UTC);
    private final LocalDate today = now.toLocalDate();
    private final YearMonth currentMonth = YearMonth.from(today);

    @BeforeEach
    void setUp() {
        service = new TransactionOperationService(
                currencyRateService,
                currencyConversionService,
                monthlySpendingRepository,
                expenseLimitRepository,
                limitCalculationService,
                transactionOperationRepository,
                unprocessedTransactionRepository,
                mapper,
                transactionOperations
        );
    }

    @Test
    @DisplayName("Should successfully save USD transaction without calling CurrencyRateService")
    void shouldSaveUsdTransactionWithoutCallingRateService() {
        RegisterTransactionRequestDto request = new RegisterTransactionRequestDto(
                "0000000123",
                "9999999999",
                "USD",
                new BigDecimal("150.00"),
                "product",
                now
        );

        AccountNumber accountFrom = AccountNumber.of("0000000123");
        AccountMonthlySpending spending = AccountMonthlySpending.createDefault(accountFrom, ExpenseCategory.PRODUCT, currentMonth);

        when(monthlySpendingRepository.getAndLock(accountFrom, ExpenseCategory.PRODUCT, currentMonth))
                .thenReturn(spending);
        when(expenseLimitRepository.findActiveLimit(accountFrom, ExpenseCategory.PRODUCT, now))
                .thenReturn(Optional.empty()); // default 1000 limit

        when(transactionOperationRepository.save(any(TransactionOperation.class)))
                .thenAnswer(inv -> {
                    TransactionOperation entity = inv.getArgument(0);
                    entity.setId(1L);
                    return entity;
                });

        TransactionResponseDto response = service.saveTransactionOperation(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.accountFrom()).isEqualTo("0000000123");
        assertThat(response.sum()).isEqualByComparingTo("150.00");
        assertThat(response.currencyShortname()).isEqualTo("USD");
        assertThat(response.limitExceeded()).isFalse();

        verifyNoInteractions(currencyRateService);
        verify(monthlySpendingRepository).save(spending);
        assertThat(spending.getTotalSpentUsd()).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Should convert foreign currency to USD and flag limitExceeded when limit is exceeded")
    void shouldConvertCurrencyAndFlagExceededLimit() {
        RegisterTransactionRequestDto request = new RegisterTransactionRequestDto(
                "0000000123",
                "9999999999",
                "KZT",
                new BigDecimal("500000.00"),
                "product",
                now
        );

        AccountNumber accountFrom = AccountNumber.of("0000000123");
        // Prior spent = 500 USD, custom limit = 800 USD
        AccountMonthlySpending spending = AccountMonthlySpending.builder()
                .accountFrom(accountFrom)
                .expenseCategory(ExpenseCategory.PRODUCT)
                .yearMonth(currentMonth)
                .totalSpentUsd(new BigDecimal("500.00"))
                .build();

        ExchangeRate rate = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(today)
                .closeRate(new BigDecimal("0.0020")) // 500,000 * 0.0020 = 1000.00 USD -> 500 + 1000 = 1500 > 800
                .build();

        ExpenseLimit customLimit = ExpenseLimit.builder()
                .id(10L)
                .accountFrom(accountFrom)
                .expenseCategory(ExpenseCategory.PRODUCT)
                .limitSum(new BigDecimal("800.00"))
                .limitCurrency(CurrencyCode.USD)
                .limitDateTime(now.minusDays(5))
                .build();

        when(currencyRateService.getOrFetchRate("KZT/USD", today)).thenReturn(Optional.of(rate));
        when(monthlySpendingRepository.getAndLock(accountFrom, ExpenseCategory.PRODUCT, currentMonth))
                .thenReturn(spending);
        when(expenseLimitRepository.findActiveLimit(accountFrom, ExpenseCategory.PRODUCT, now))
                .thenReturn(Optional.of(customLimit));

        when(transactionOperationRepository.save(any(TransactionOperation.class)))
                .thenAnswer(inv -> {
                    TransactionOperation entity = inv.getArgument(0);
                    entity.setId(2L);
                    return entity;
                });

        TransactionResponseDto response = service.saveTransactionOperation(request);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.limitExceeded()).isTrue();
        assertThat(spending.getTotalSpentUsd()).isEqualByComparingTo("1500.00");
        verify(monthlySpendingRepository).save(spending);
    }

    @Test
    @DisplayName("Should park transaction in DLQ and throw TransactionParkedException when rate is unavailable")
    void shouldParkInDlqWhenRateUnavailable() {
        RegisterTransactionRequestDto request = new RegisterTransactionRequestDto(
                "0000000123",
                "9999999999",
                "RUB",
                new BigDecimal("5000.00"),
                "service",
                now
        );

        when(currencyRateService.getOrFetchRate("RUB/USD", today)).thenReturn(Optional.empty());
        when(unprocessedTransactionRepository.save(any(UnprocessedTransaction.class)))
                .thenAnswer(inv -> {
                    UnprocessedTransaction entity = inv.getArgument(0);
                    entity.setId(99L);
                    return entity;
                });

        assertThatThrownBy(() -> service.saveTransactionOperation(request))
                .isInstanceOf(TransactionParkedException.class)
                .hasMessageContaining("Exchange rate unavailable for RUB/USD");

        verify(unprocessedTransactionRepository).save(any(UnprocessedTransaction.class));
        verifyNoInteractions(monthlySpendingRepository);
        verifyNoInteractions(transactionOperationRepository);
    }

    @Test
    @DisplayName("Should successfully execute pipeline for recovered DLQ transaction")
    void shouldCompleteRecoveredTransaction() {
        AccountNumber accountFrom = AccountNumber.of("0000000123");
        AccountNumber accountTo = AccountNumber.of("9999999999");
        UnprocessedTransaction record = UnprocessedTransaction.create(
                accountFrom,
                accountTo,
                CurrencyCode.of("KZT"),
                new BigDecimal("10000.00"),
                ExpenseCategory.PRODUCT,
                now,
                "Initial rate failure"
        );
        record.setId(77L);

        ExchangeRate rate = ExchangeRate.builder()
                .currencyPair("KZT/USD")
                .rateDate(today)
                .closeRate(new BigDecimal("0.0020")) // 10,000 * 0.0020 = 20.00 USD
                .build();

        AccountMonthlySpending spending = AccountMonthlySpending.createDefault(accountFrom, ExpenseCategory.PRODUCT, currentMonth);

        when(currencyRateService.getOrFetchRate("KZT/USD", today)).thenReturn(Optional.of(rate));
        when(monthlySpendingRepository.getAndLock(accountFrom, ExpenseCategory.PRODUCT, currentMonth)).thenReturn(spending);
        when(expenseLimitRepository.findActiveLimit(accountFrom, ExpenseCategory.PRODUCT, now)).thenReturn(Optional.empty());
        when(transactionOperationRepository.save(any(TransactionOperation.class)))
                .thenAnswer(inv -> {
                    TransactionOperation entity = inv.getArgument(0);
                    entity.setId(77L);
                    return entity;
                });

        service.completeRecoveredTransaction(record);

        verify(monthlySpendingRepository).save(spending);
        assertThat(spending.getTotalSpentUsd()).isEqualByComparingTo("20.00");
        verify(transactionOperationRepository).save(argThat(tx ->
                tx.getSum().compareTo(new BigDecimal("10000.00")) == 0 && !tx.isLimitExceeded()
        ));
    }

    @Test
    @DisplayName("Should queue transaction in DLQ when earlier transaction for same account and category is pending")
    void shouldQueueInDlqWhenEarlierTransactionIsPending() {
        RegisterTransactionRequestDto request = new RegisterTransactionRequestDto(
                "0000000123",
                "9999999999",
                "USD",
                new BigDecimal("600.00"),
                "product",
                now
        );

        AccountNumber accountFrom = AccountNumber.of("0000000123");
        when(unprocessedTransactionRepository.hasUnresolvedTransactions(accountFrom, ExpenseCategory.PRODUCT, currentMonth))
                .thenReturn(true);

        when(unprocessedTransactionRepository.save(any(UnprocessedTransaction.class)))
                .thenAnswer(inv -> {
                    UnprocessedTransaction entity = inv.getArgument(0);
                    entity.setId(101L);
                    return entity;
                });

        assertThatThrownBy(() -> service.saveTransactionOperation(request))
                .isInstanceOf(TransactionParkedException.class)
                .hasMessageContaining("Earlier transaction is pending or unresolved for this account");

        verify(unprocessedTransactionRepository).save(argThat(entity ->
                entity.getAccountFrom().equals(accountFrom)
                        && entity.getExpenseCategory().equals(ExpenseCategory.PRODUCT)
                        && entity.getSum().compareTo(new BigDecimal("600.00")) == 0
                        && entity.getFailureReason().contains("Queued behind earlier unresolved transaction")
        ));

        // Strict verification: DB balance is NOT modified and transaction is NOT saved directly
        verifyNoInteractions(monthlySpendingRepository);
        verifyNoInteractions(transactionOperationRepository);
    }
}
