package com.example.spending.service.infrastructure.repository;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.TransactionRecord;
import com.example.spending.service.domain.repository.TransactionRecordRepository;
import com.example.spending.service.infrastructure.mapper.TransactionRecordMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.spending.service.infrastructure.jooq.Tables.EXCHANGE_RATES;
import static com.example.spending.service.infrastructure.jooq.Tables.TRANSACTION_RECORDS;

@Repository
@RequiredArgsConstructor
public class JooqTransactionRecordRepository implements TransactionRecordRepository {

    private final DSLContext dsl;
    private final TransactionRecordMapper mapper;

    @Override
    public TransactionRecord save(TransactionRecord transaction) {
        var record = dsl.insertInto(TRANSACTION_RECORDS)
                .set(TRANSACTION_RECORDS.ACCOUNT_FROM, transaction.getAccountFrom().value())
                .set(TRANSACTION_RECORDS.ACCOUNT_TO, transaction.getAccountTo().value())
                .set(TRANSACTION_RECORDS.CURRENCY_SHORTNAME, transaction.getCurrencyShortname().code())
                .set(TRANSACTION_RECORDS.SUM, transaction.getSum())
                .set(TRANSACTION_RECORDS.EXPENSE_CATEGORY, transaction.getExpenseCategory().getCode())
                .set(TRANSACTION_RECORDS.DATETIME, transaction.getDateTime())
                .set(TRANSACTION_RECORDS.LIMIT_EXCEEDED, transaction.isLimitExceeded())
                .set(TRANSACTION_RECORDS.VERSION, transaction.getVersion() != null ? transaction.getVersion() : 0L)
                .returning()
                .fetchOne();

        return mapper.map(record);
    }

    @Override
    public Optional<TransactionRecord> findById(Long id) {
        return dsl.selectFrom(TRANSACTION_RECORDS)
                .where(TRANSACTION_RECORDS.ID.eq(id))
                .fetchOptional(mapper);
    }

    @Override
    public void lockAccount(AccountNumber accountFrom) {
        dsl.execute("SELECT pg_advisory_xact_lock(hashtext(?))", accountFrom.value());
    }

    @Override
    public BigDecimal sumMonthlyExpensesInUsd(AccountNumber accountFrom, ExpenseCategory category, OffsetDateTime from, OffsetDateTime to) {
        BigDecimal sum = dsl.select(DSL.coalesce(DSL.sum(
                        DSL.when(TRANSACTION_RECORDS.CURRENCY_SHORTNAME.eq("USD"), TRANSACTION_RECORDS.SUM)
                                .otherwise(TRANSACTION_RECORDS.SUM.mul(DSL.coalesce(EXCHANGE_RATES.CLOSE_RATE, BigDecimal.ONE)))
                ), BigDecimal.ZERO))
                .from(TRANSACTION_RECORDS)
                .leftJoin(EXCHANGE_RATES)
                .on(EXCHANGE_RATES.CURRENCY_PAIR.eq(TRANSACTION_RECORDS.CURRENCY_SHORTNAME.concat("/USD")))
                .and(EXCHANGE_RATES.RATE_DATE.eq(TRANSACTION_RECORDS.DATETIME.cast(SQLDataType.LOCALDATE)))
                .where(TRANSACTION_RECORDS.ACCOUNT_FROM.eq(accountFrom.value()))
                .and(TRANSACTION_RECORDS.EXPENSE_CATEGORY.eq(category.getCode()))
                .and(TRANSACTION_RECORDS.DATETIME.ge(from))
                .and(TRANSACTION_RECORDS.DATETIME.lt(to))
                .fetchOne(0, BigDecimal.class);

        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Override
    public List<TransactionRecord> findExceededTransactions(AccountNumber accountFrom) {
        return dsl.selectFrom(TRANSACTION_RECORDS)
                .where(TRANSACTION_RECORDS.ACCOUNT_FROM.eq(accountFrom.value()))
                .and(TRANSACTION_RECORDS.LIMIT_EXCEEDED.isTrue())
                .orderBy(TRANSACTION_RECORDS.DATETIME.asc())
                .fetch(mapper);
    }
}
