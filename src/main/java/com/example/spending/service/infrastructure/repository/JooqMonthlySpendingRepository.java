package com.example.spending.service.infrastructure.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.AccountMonthlySpending;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.repository.MonthlySpendingRepository;
import com.example.spending.service.infrastructure.mapper.MonthlySpendingMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.YearMonth;

import static com.example.spending.service.infrastructure.jooq.Tables.ACCOUNT_MONTHLY_SPENDING;

@Repository
@RequiredArgsConstructor
public class JooqMonthlySpendingRepository implements MonthlySpendingRepository {

    private final DSLContext dsl;
    private final MonthlySpendingMapper mapper;

    @Override
    public AccountMonthlySpending getAndLock(AccountNumber accountFrom, ExpenseCategory category, YearMonth yearMonth) {
        String ym = yearMonth.toString();

        // Ensure row exists via INSERT ... ON CONFLICT DO NOTHING
        dsl.insertInto(ACCOUNT_MONTHLY_SPENDING)
                .set(ACCOUNT_MONTHLY_SPENDING.ACCOUNT_FROM, accountFrom.value())
                .set(ACCOUNT_MONTHLY_SPENDING.EXPENSE_CATEGORY, category.getCode())
                .set(ACCOUNT_MONTHLY_SPENDING.YEAR_MONTH, ym)
                .set(ACCOUNT_MONTHLY_SPENDING.TOTAL_SPENT_USD, BigDecimal.ZERO)
                .onConflict(ACCOUNT_MONTHLY_SPENDING.ACCOUNT_FROM, ACCOUNT_MONTHLY_SPENDING.EXPENSE_CATEGORY, ACCOUNT_MONTHLY_SPENDING.YEAR_MONTH)
                .doNothing()
                .execute();

        // Acquire exclusive pessimistic row lock (FOR UPDATE) and map to domain entity
        return dsl.selectFrom(ACCOUNT_MONTHLY_SPENDING)
                .where(ACCOUNT_MONTHLY_SPENDING.ACCOUNT_FROM.eq(accountFrom.value()))
                .and(ACCOUNT_MONTHLY_SPENDING.EXPENSE_CATEGORY.eq(category.getCode()))
                .and(ACCOUNT_MONTHLY_SPENDING.YEAR_MONTH.eq(ym))
                .forUpdate()
                .fetchOne(mapper);
    }

    @Override
    public void save(AccountMonthlySpending spending) {
        String ym = spending.getYearMonth().toString();
        dsl.update(ACCOUNT_MONTHLY_SPENDING)
                .set(ACCOUNT_MONTHLY_SPENDING.TOTAL_SPENT_USD, spending.getTotalSpentUsd())
                .set(ACCOUNT_MONTHLY_SPENDING.UPDATED_AT, spending.getUpdatedAt())
                .where(ACCOUNT_MONTHLY_SPENDING.ACCOUNT_FROM.eq(spending.getAccountFrom().value()))
                .and(ACCOUNT_MONTHLY_SPENDING.EXPENSE_CATEGORY.eq(spending.getExpenseCategory().getCode()))
                .and(ACCOUNT_MONTHLY_SPENDING.YEAR_MONTH.eq(ym))
                .execute();
    }
}
