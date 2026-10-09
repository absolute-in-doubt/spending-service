package com.example.spending.service.infrastructure.adapter.out.persistence;

import com.example.spending.service.application.port.in.dto.ExceededTransactionResponseDto;
import com.example.spending.service.application.port.out.TransactionQueryRepository;
import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.TransactionOperation;
import com.example.spending.service.domain.repository.TransactionOperationRepository;
import com.example.spending.service.infrastructure.adapter.out.persistence.mapper.TransactionOperationMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static com.example.spending.service.infrastructure.jooq.Tables.EXPENSE_LIMITS;
import static com.example.spending.service.infrastructure.jooq.Tables.TRANSACTION_RECORDS;

@Repository
@RequiredArgsConstructor
public class JooqTransactionOperationRepository implements TransactionOperationRepository, TransactionQueryRepository {

    private final DSLContext dsl;
    private final TransactionOperationMapper mapper;

    @Override
    public TransactionOperation save(TransactionOperation transaction) {
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
    public Optional<TransactionOperation> findById(Long id) {
        return dsl.selectFrom(TRANSACTION_RECORDS)
                .where(TRANSACTION_RECORDS.ID.eq(id))
                .fetchOptional(mapper);
    }

    @Override
    public List<TransactionOperation> findExceededTransactions(AccountNumber accountFrom) {
        return dsl.selectFrom(TRANSACTION_RECORDS)
                .where(TRANSACTION_RECORDS.ACCOUNT_FROM.eq(accountFrom.value()))
                .and(TRANSACTION_RECORDS.LIMIT_EXCEEDED.isTrue())
                .orderBy(TRANSACTION_RECORDS.DATETIME.asc())
                .fetch(mapper);
    }

    @Override
    public List<ExceededTransactionResponseDto> findExceededTransactionsWithLimit(AccountNumber accountFrom) {
        var t = TRANSACTION_RECORDS.as("t");
        var l = EXPENSE_LIMITS.as("l");

        // Subquery grouping by transaction ID with aggregating function MAX(limit_datetime)
        var latestLimitSubquery = dsl.select(
                        t.ID.as("transaction_id"),
                        DSL.max(l.LIMIT_DATETIME).as("max_limit_dt")
                )
                .from(t)
                .join(l)
                .on(l.ACCOUNT_FROM.eq(t.ACCOUNT_FROM))
                .and(l.EXPENSE_CATEGORY.eq(t.EXPENSE_CATEGORY))
                .and(l.LIMIT_DATETIME.le(t.DATETIME))
                .groupBy(t.ID)
                .asTable("latest_l");

        var tr = TRANSACTION_RECORDS.as("tr");
        var el = EXPENSE_LIMITS.as("el");

        return dsl.select(
                        tr.ACCOUNT_FROM,
                        tr.ACCOUNT_TO,
                        tr.CURRENCY_SHORTNAME,
                        tr.SUM,
                        tr.EXPENSE_CATEGORY,
                        tr.DATETIME,
                        DSL.coalesce(el.LIMIT_SUM, DSL.val(new BigDecimal("1000.00"))).as("limit_sum"),
                        el.LIMIT_DATETIME.as("limit_datetime"),
                        DSL.coalesce(el.LIMIT_CURRENCY_SHORTNAME, DSL.val("USD")).as("limit_currency_shortname")
                )
                .from(tr)
                .leftJoin(latestLimitSubquery)
                .on(latestLimitSubquery.field("transaction_id", Long.class).eq(tr.ID))
                .leftJoin(el)
                .on(el.ACCOUNT_FROM.eq(tr.ACCOUNT_FROM))
                .and(el.EXPENSE_CATEGORY.eq(tr.EXPENSE_CATEGORY))
                .and(el.LIMIT_DATETIME.eq(latestLimitSubquery.field("max_limit_dt", OffsetDateTime.class)))
                .where(tr.ACCOUNT_FROM.eq(accountFrom.value()))
                .and(tr.LIMIT_EXCEEDED.isTrue())
                .orderBy(tr.DATETIME.asc())
                .fetch(r -> new ExceededTransactionResponseDto(
                        r.get(tr.ACCOUNT_FROM),
                        r.get(tr.ACCOUNT_TO),
                        r.get(tr.CURRENCY_SHORTNAME),
                        r.get(tr.SUM),
                        r.get(tr.EXPENSE_CATEGORY),
                        r.get(tr.DATETIME),
                        r.get("limit_sum", BigDecimal.class),
                        r.get("limit_datetime", OffsetDateTime.class),
                        r.get("limit_currency_shortname", String.class)
                ));
    }
}
