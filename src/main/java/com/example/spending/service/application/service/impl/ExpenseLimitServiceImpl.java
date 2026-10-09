package com.example.spending.service.application.service.impl;

import com.example.spending.service.application.mapper.ExpenseLimitDtoMapper;
import com.example.spending.service.application.service.ExpenseLimitService;
import com.example.spending.service.application.port.in.dto.CreateExpenseLimitRequestDto;
import com.example.spending.service.application.port.in.dto.ExpenseLimitResponseDto;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseLimit;
import com.example.spending.service.domain.repository.ExpenseLimitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of ExpenseLimitService inbound port.
 * Enforces business rules: automatic timestamping via java.time.Clock,
 * USD-only limit currency, and append-only limit history.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseLimitServiceImpl implements ExpenseLimitService {

    private final ExpenseLimitRepository expenseLimitRepository;
    private final ExpenseLimitDtoMapper mapper;
    private final Clock clock;

    @Override
    @Transactional
    public ExpenseLimitResponseDto createLimit(CreateExpenseLimitRequestDto request) {
        Objects.requireNonNull(request, "CreateExpenseLimitRequestDto must not be null");

        // Validate currency if specified: must be USD
        if (request.limitCurrencyShortname() != null && !request.limitCurrencyShortname().isBlank()) {
            if (!"USD".equalsIgnoreCase(request.limitCurrencyShortname().trim())) {
                throw new IllegalArgumentException(
                        "Expense limits can only be set in USD, but received: " + request.limitCurrencyShortname()
                );
            }
        }

        // set limit_datetime from Clock
        OffsetDateTime now = OffsetDateTime.now(clock);

        ExpenseLimit entity = mapper.toEntity(request, now);
        ExpenseLimit saved = expenseLimitRepository.save(entity);

        log.info("Created new expense limit ID {} for account {} (category: {}, sum: {} USD at {})",
                saved.getId(), saved.getAccountFrom(), saved.getExpenseCategory(), saved.getLimitSum(), saved.getLimitDateTime());

        return mapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseLimitResponseDto> getLimits(String accountFrom) {
        Objects.requireNonNull(accountFrom, "account_from must not be null");
        AccountNumber account = AccountNumber.of(accountFrom);

        List<ExpenseLimit> limits = expenseLimitRepository.findAllByAccount(account);
        log.debug("Found {} limits for account {}", limits.size(), accountFrom);

        return mapper.toDtoList(limits);
    }
}
