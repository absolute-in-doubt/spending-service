package com.example.spending.service.application.service;

import com.example.spending.service.application.mapper.DefaultExpenseLimitDtoMapper;
import com.example.spending.service.application.mapper.ExpenseLimitDtoMapper;
import com.example.spending.service.application.port.in.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.application.port.in.dto.ExpenseLimitResponseDto;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.CurrencyCode;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.ExpenseLimit;
import com.example.spending.service.domain.repository.ExpenseLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseLimitServiceTest {

    @Mock
    private ExpenseLimitRepository expenseLimitRepository;

    private final ExpenseLimitDtoMapper mapper = new DefaultExpenseLimitDtoMapper();
    private Clock fixedClock;
    private ExpenseLimitService service;

    private final OffsetDateTime expectedNow = OffsetDateTime.of(2026, 10, 9, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-10-09T12:00:00Z"), ZoneOffset.UTC);
        service = new ExpenseLimitService(expenseLimitRepository, mapper, fixedClock);
    }

    @Test
    @DisplayName("Should create limit with automated Clock timestamp and force USD currency")
    void shouldCreateLimitWithClockTimeAndUsdCurrency() {
        CreateExpenseLimitRequestDto request = new CreateExpenseLimitRequestDto(
                "0000000123",
                "product",
                new BigDecimal("2000.00"),
                "USD"
        );

        when(expenseLimitRepository.save(any(ExpenseLimit.class)))
                .thenAnswer(inv -> {
                    ExpenseLimit entity = inv.getArgument(0);
                    entity.setId(10L);
                    return entity;
                });

        ExpenseLimitResponseDto response = service.createLimit(request);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.accountFrom()).isEqualTo("0000000123");
        assertThat(response.expenseCategory()).isEqualTo("product");
        assertThat(response.limitSum()).isEqualByComparingTo("2000.00");
        assertThat(response.limitCurrencyShortname()).isEqualTo("USD");
        assertThat(response.limitDateTime()).isEqualTo(expectedNow);

        verify(expenseLimitRepository).save(argThat(entity ->
                entity.getLimitDateTime().equals(expectedNow)
                        && entity.getLimitCurrency().equals(CurrencyCode.USD)
                        && entity.getExpenseCategory().equals(ExpenseCategory.PRODUCT)
        ));
    }

    @Test
    @DisplayName("Should default currency to USD when limitCurrencyShortname is omitted")
    void shouldDefaultCurrencyToUsdWhenOmitted() {
        CreateExpenseLimitRequestDto request = new CreateExpenseLimitRequestDto(
                "0000000123",
                "service",
                new BigDecimal("500.00"),
                null
        );

        when(expenseLimitRepository.save(any(ExpenseLimit.class)))
                .thenAnswer(inv -> {
                    ExpenseLimit entity = inv.getArgument(0);
                    entity.setId(11L);
                    return entity;
                });

        ExpenseLimitResponseDto response = service.createLimit(request);

        assertThat(response.limitCurrencyShortname()).isEqualTo("USD");
        verify(expenseLimitRepository).save(argThat(e -> e.getLimitCurrency().equals(CurrencyCode.USD)));
    }

    @Test
    @DisplayName("Should throw readable IllegalArgumentException when non-USD currency is requested")
    void shouldRejectNonUsdCurrency() {
        CreateExpenseLimitRequestDto request = new CreateExpenseLimitRequestDto(
                "0000000123",
                "product",
                new BigDecimal("1000.00"),
                "EUR"
        );

        assertThatThrownBy(() -> service.createLimit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Expense limits can only be set in USD, but received: EUR");

        verifyNoInteractions(expenseLimitRepository);
    }

    @Test
    @DisplayName("Should throw readable IllegalArgumentException when account number is invalid")
    void shouldRejectInvalidAccountNumber() {
        CreateExpenseLimitRequestDto request = new CreateExpenseLimitRequestDto(
                "invalid",
                "product",
                new BigDecimal("1000.00"),
                "USD"
        );

        assertThatThrownBy(() -> service.createLimit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account number must consist of exactly 10 digits");

        verifyNoInteractions(expenseLimitRepository);
    }

    @Test
    @DisplayName("Should throw readable IllegalArgumentException when category is invalid")
    void shouldRejectInvalidCategory() {
        CreateExpenseLimitRequestDto request = new CreateExpenseLimitRequestDto(
                "0000000123",
                "unknown_cat",
                new BigDecimal("1000.00"),
                "USD"
        );

        assertThatThrownBy(() -> service.createLimit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown expense category: unknown_cat");

        verifyNoInteractions(expenseLimitRepository);
    }

    @Test
    @DisplayName("Should retrieve all limits for specified account")
    void shouldRetrieveLimitsForAccount() {
        AccountNumber account = AccountNumber.of("0000000123");
        ExpenseLimit limit1 = ExpenseLimit.builder()
                .id(1L)
                .accountFrom(account)
                .expenseCategory(ExpenseCategory.PRODUCT)
                .limitSum(new BigDecimal("1000.00"))
                .limitCurrency(CurrencyCode.USD)
                .limitDateTime(expectedNow.minusDays(5))
                .build();

        ExpenseLimit limit2 = ExpenseLimit.builder()
                .id(2L)
                .accountFrom(account)
                .expenseCategory(ExpenseCategory.SERVICE)
                .limitSum(new BigDecimal("2000.00"))
                .limitCurrency(CurrencyCode.USD)
                .limitDateTime(expectedNow)
                .build();

        when(expenseLimitRepository.findAllByAccount(account)).thenReturn(List.of(limit2, limit1));

        List<ExpenseLimitResponseDto> result = service.getLimits("0000000123");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(2L);
        assertThat(result.get(1).id()).isEqualTo(1L);
        verify(expenseLimitRepository).findAllByAccount(account);
    }
}
