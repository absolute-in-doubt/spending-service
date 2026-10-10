package com.example.spending.service.application.service.impl;

import com.example.spending.service.application.exception.TransactionParkedException;
import com.example.spending.service.application.mapper.TransactionOperationDtoMapper;
import com.example.spending.service.application.service.TransactionOperationService;
import com.example.spending.service.application.service.TransactionRecoveryHandler;
import com.example.spending.service.application.port.in.dto.ExceededTransactionResponseDto;
import com.example.spending.service.application.port.in.dto.RegisterTransactionRequestDto;
import com.example.spending.service.application.port.in.dto.TransactionResponseDto;
import com.example.spending.service.application.port.out.TransactionQueryRepository;
import com.example.spending.service.application.service.CurrencyRateService;
import com.example.spending.service.domain.model.*;
import com.example.spending.service.domain.repository.ExpenseLimitRepository;
import com.example.spending.service.domain.repository.MonthlySpendingRepository;
import com.example.spending.service.domain.repository.TransactionOperationRepository;
import com.example.spending.service.domain.repository.UnprocessedTransactionRepository;
import com.example.spending.service.domain.service.CurrencyConversionService;
import com.example.spending.service.domain.service.LimitCalculationService;
import com.example.spending.service.domain.util.DateTimeUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of TransactionOperationService and TransactionRecoveryHandler.
 * Orchestrates receipt, limit evaluation, and persistence of transaction operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionOperationServiceImpl implements TransactionOperationService, TransactionRecoveryHandler {

    private final CurrencyRateService currencyRateService;
    private final CurrencyConversionService currencyConversionService;
    private final MonthlySpendingRepository monthlySpendingRepository;
    private final ExpenseLimitRepository expenseLimitRepository;
    private final LimitCalculationService limitCalculationService;
    private final TransactionOperationRepository transactionOperationRepository;
    private final TransactionQueryRepository transactionQueryRepository;
    private final UnprocessedTransactionRepository unprocessedTransactionRepository;
    private final TransactionOperationDtoMapper mapper;
    private final TransactionOperations transactionOperations;

    @Override
    public TransactionResponseDto saveTransactionOperation(RegisterTransactionRequestDto request) {
        Objects.requireNonNull(request, "RegisterTransactionRequestDto must not be null");

        AccountNumber accountFrom = AccountNumber.of(request.accountFrom());
        AccountNumber accountTo = AccountNumber.of(request.accountTo());
        CurrencyCode currency = CurrencyCode.of(request.currencyShortname());
        ExpenseCategory category = ExpenseCategory.fromCode(request.expenseCategory());
        OffsetDateTime dateTime = request.dateTime();
        BigDecimal sum = request.sum();
        LocalDate txDate = DateTimeUtils.toBaseLocalDate(dateTime);
        YearMonth yearMonth = DateTimeUtils.toBaseYearMonth(dateTime);

        // 1. FIFO Head-of-Line Barrier: if earlier transactions are unresolved (PENDING or FAILED), queue behind them
        if (unprocessedTransactionRepository.hasUnresolvedTransactions(accountFrom, category, yearMonth)) {
            UnprocessedTransaction parked = UnprocessedTransaction.create(
                    accountFrom,
                    accountTo,
                    currency,
                    sum,
                    category,
                    dateTime,
                    "Queued behind earlier unresolved transaction for account " + accountFrom.value()
            );
            parked = unprocessedTransactionRepository.save(parked);
            log.info("Earlier unresolved transaction exists for account {} and category {}. Queued in DLQ with ID {}",
                    accountFrom.value(), category.getCode(), parked.getId());
            throw new TransactionParkedException(
                    "Earlier transaction is pending or unresolved for this account. Queued in DLQ.",
                    parked.getId()
            );
        }

        // 2. Rate retrieval & USD conversion (performed OUTSIDE database transaction)
        BigDecimal amountUsd;
        if (CurrencyCode.USD.equals(currency)) {
            amountUsd = sum;
        } else {
            String pair = currency.code() + "/USD";
            Optional<ExchangeRate> rateOpt = currencyRateService.getOrFetchRate(pair, txDate);
            if (rateOpt.isEmpty()) {
                UnprocessedTransaction parked = UnprocessedTransaction.create(
                        accountFrom,
                        accountTo,
                        currency,
                        sum,
                        category,
                        dateTime,
                        "Exchange rate unavailable for " + pair + " on " + txDate
                );
                unprocessedTransactionRepository.save(parked);
                log.warn("Rate unavailable for {} on {}. Transaction parked in DLQ with ID {}",
                        pair, txDate, parked.getId());
                throw new TransactionParkedException(
                        "Exchange rate unavailable for " + pair + ". Transaction parked in DLQ for retry.",
                        parked.getId()
                );
            }
            amountUsd = currencyConversionService.convertToUsd(sum, currency, rateOpt.get());
        }

        // 3. Transactional persistence & limit evaluation
        return transactionOperations.execute(status ->
                executeTransactionPipeline(request, accountFrom, category, dateTime, amountUsd)
        );
    }

    @Override
    public void completeRecoveredTransaction(UnprocessedTransaction record) {
        Objects.requireNonNull(record, "UnprocessedTransaction record must not be null");

        CurrencyCode currency = record.getCurrencyShortname();
        LocalDate txDate = DateTimeUtils.toBaseLocalDate(record.getDateTime());
        String pair = currency.code() + "/USD";

        ExchangeRate rate = currencyRateService.getOrFetchRate(pair, txDate)
                .orElseThrow(() -> new IllegalStateException("Expected rate to be available in DB for " + pair));
        BigDecimal amountUsd = currencyConversionService.convertToUsd(record.getSum(), currency, rate);

        RegisterTransactionRequestDto dto = new RegisterTransactionRequestDto(
                record.getAccountFrom().value(),
                record.getAccountTo().value(),
                record.getCurrencyShortname().code(),
                record.getSum(),
                record.getExpenseCategory().getCode(),
                record.getDateTime()
        );

        transactionOperations.executeWithoutResult(status -> {
            executeTransactionPipeline(
                    dto,
                    record.getAccountFrom(),
                    record.getExpenseCategory(),
                    record.getDateTime(),
                    amountUsd
            );
        });

        log.info("Recovered transaction ID {} successfully executed through transaction pipeline", record.getId());
    }

    private TransactionResponseDto executeTransactionPipeline(
            RegisterTransactionRequestDto request,
            AccountNumber accountFrom,
            ExpenseCategory category,
            OffsetDateTime dateTime,
            BigDecimal amountUsd
    ) {
        YearMonth yearMonth = DateTimeUtils.toBaseYearMonth(dateTime);

        // Step 1: Pessimistic lock on AccountMonthlySpending (SELECT ... FOR UPDATE)
        AccountMonthlySpending spending = monthlySpendingRepository.getAndLock(accountFrom, category, yearMonth);

        // Step 2: Find an active limit for this account, category, and datetime
        Optional<ExpenseLimit> activeLimit = expenseLimitRepository.findActiveLimit(accountFrom, category, dateTime);

        // Step 3: Evaluate whether the limit is exceeded
        boolean limitExceeded = limitCalculationService.isLimitExceeded(
                activeLimit.orElse(null),
                spending.getTotalSpentUsd(),
                amountUsd
        );

        if(activeLimit.isEmpty())
            log.info("Limit not found for account from: {} and category: {}", accountFrom.value(), category);
        else
            log.info("Limit evaluation result for transaction from {}:\n usd tx amount: {}, usd limit: {}, limit exceeded: {}", accountFrom.value(), amountUsd, activeLimit.get(), limitExceeded);

        // Step 4: Accumulate expense in AccountMonthlySpending and persist
        spending.addExpense(amountUsd);
        monthlySpendingRepository.save(spending);

        // Step 5: Map to TransactionOperation entity using mapper and persist
        TransactionOperation transaction = mapper.toEntity(request, limitExceeded);
        TransactionOperation saved = transactionOperationRepository.save(transaction);
        log.info("TransactionOperation saved with ID {}, limitExceeded={}", saved.getId(), saved.isLimitExceeded());

        // Step 6: Map to TransactionResponseDto and return
        return mapper.toDto(saved);
    }

    @Override
    public List<ExceededTransactionResponseDto> getExceededTransactions(String accountFrom) {
        Objects.requireNonNull(accountFrom, "account_from must not be null");
        AccountNumber account = AccountNumber.of(accountFrom);
        log.debug("Retrieving exceeded transactions for account: {}", accountFrom);
        return transactionQueryRepository.findExceededTransactionsWithLimit(account);
    }
}
