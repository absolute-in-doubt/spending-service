package com.example.spending.service.infrastructure.adapter.out.persistence;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.ExpenseLimit;
import com.example.spending.service.domain.repository.ExpenseLimitRepository;
import com.example.spending.service.infrastructure.adapter.out.persistence.mapper.ExpenseLimitMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.spending.service.infrastructure.jooq.Tables.EXPENSE_LIMITS;

@Repository
@RequiredArgsConstructor
public class JooqExpenseLimitRepository implements ExpenseLimitRepository {

    private final DSLContext dsl;
    private final ExpenseLimitMapper mapper;

    @Override
    public ExpenseLimit save(ExpenseLimit limit) {
        var record = dsl.insertInto(EXPENSE_LIMITS)
                .set(EXPENSE_LIMITS.ACCOUNT_FROM, limit.getAccountFrom().value())
                .set(EXPENSE_LIMITS.EXPENSE_CATEGORY, limit.getExpenseCategory().getCode())
                .set(EXPENSE_LIMITS.LIMIT_SUM, limit.getLimitSum())
                .set(EXPENSE_LIMITS.LIMIT_CURRENCY_SHORTNAME, limit.getLimitCurrency() != null ? limit.getLimitCurrency().code() : "USD")
                .set(EXPENSE_LIMITS.LIMIT_DATETIME, limit.getLimitDateTime())
                .returning()
                .fetchOne();

        return mapper.map(record);
    }

    @Override
    public Optional<ExpenseLimit> findActiveLimit(AccountNumber accountFrom, ExpenseCategory category, OffsetDateTime dateTime) {
        return dsl.selectFrom(EXPENSE_LIMITS)
                .where(EXPENSE_LIMITS.ACCOUNT_FROM.eq(accountFrom.value()))
                .and(EXPENSE_LIMITS.EXPENSE_CATEGORY.eq(category.getCode()))
                .and(EXPENSE_LIMITS.LIMIT_DATETIME.le(dateTime))
                .orderBy(EXPENSE_LIMITS.LIMIT_DATETIME.desc())
                .limit(1)
                .fetchOptional(mapper);
    }

    @Override
    public List<ExpenseLimit> findAllByAccount(AccountNumber accountFrom) {
        return dsl.selectFrom(EXPENSE_LIMITS)
                .where(EXPENSE_LIMITS.ACCOUNT_FROM.eq(accountFrom.value()))
                .orderBy(EXPENSE_LIMITS.LIMIT_DATETIME.desc())
                .fetch(mapper);
    }
}
