package com.example.spending.service.infrastructure.adapter.out.persistence;

import com.example.spending.service.domain.model.AccountNumber;
import com.example.spending.service.domain.model.ExpenseCategory;
import com.example.spending.service.domain.model.UnprocessedTransaction;
import com.example.spending.service.domain.repository.UnprocessedTransactionRepository;
import com.example.spending.service.domain.util.DateTimeUtils;
import com.example.spending.service.infrastructure.adapter.out.persistence.mapper.UnprocessedTransactionMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static com.example.spending.service.infrastructure.jooq.Tables.UNPROCESSED_TRANSACTIONS;

@Repository
@RequiredArgsConstructor
public class JooqUnprocessedTransactionRepository implements UnprocessedTransactionRepository {

    private final DSLContext dsl;
    private final UnprocessedTransactionMapper mapper;

    @Override
    public UnprocessedTransaction save(UnprocessedTransaction transaction) {
        var record = dsl.insertInto(UNPROCESSED_TRANSACTIONS)
                .set(UNPROCESSED_TRANSACTIONS.ACCOUNT_FROM, transaction.getAccountFrom().value())
                .set(UNPROCESSED_TRANSACTIONS.ACCOUNT_TO, transaction.getAccountTo().value())
                .set(UNPROCESSED_TRANSACTIONS.CURRENCY_SHORTNAME, transaction.getCurrencyShortname().code())
                .set(UNPROCESSED_TRANSACTIONS.SUM, transaction.getSum())
                .set(UNPROCESSED_TRANSACTIONS.EXPENSE_CATEGORY, transaction.getExpenseCategory().getCode())
                .set(UNPROCESSED_TRANSACTIONS.DATETIME, transaction.getDateTime())
                .set(UNPROCESSED_TRANSACTIONS.FAILURE_REASON, transaction.getFailureReason())
                .set(UNPROCESSED_TRANSACTIONS.STATUS, transaction.getStatus().name())
                .set(UNPROCESSED_TRANSACTIONS.RETRY_COUNT, transaction.getRetryCount())
                .set(UNPROCESSED_TRANSACTIONS.LEASE_TOKEN, transaction.getLeaseToken())
                .set(UNPROCESSED_TRANSACTIONS.LEASED_UNTIL, transaction.getLeasedUntil())
                .returning()
                .fetchOne();

        return mapper.map(record);
    }

    @Override
    public Optional<UnprocessedTransaction> findById(Long id) {
        return dsl.selectFrom(UNPROCESSED_TRANSACTIONS)
                .where(UNPROCESSED_TRANSACTIONS.ID.eq(id))
                .fetchOptional(mapper);
    }

    @Override
    public List<UnprocessedTransaction> acquireBatchForRetry(String workerToken, OffsetDateTime now, OffsetDateTime leaseUntil, int maxRetries, int limit) {
        // 1. Define the CTE with LIMIT and FOR UPDATE SKIP LOCKED
        var batch = org.jooq.impl.DSL.name("batch").as(
                dsl.select(UNPROCESSED_TRANSACTIONS.ID)
                        .from(UNPROCESSED_TRANSACTIONS)
                        .where(UNPROCESSED_TRANSACTIONS.STATUS.eq("PENDING"))
                        .and(UNPROCESSED_TRANSACTIONS.RETRY_COUNT.lt(maxRetries))
                        .and(UNPROCESSED_TRANSACTIONS.LEASED_UNTIL.isNull()
                                .or(UNPROCESSED_TRANSACTIONS.LEASED_UNTIL.lt(now)))
                        .orderBy(UNPROCESSED_TRANSACTIONS.DATETIME.asc(), UNPROCESSED_TRANSACTIONS.ID.asc())
                        .limit(limit)
                        .forUpdate()
                        .skipLocked()
        );

        // 2. Perform atomic UPDATE ... FROM batch ... RETURNING
        List<UnprocessedTransaction> acquired = dsl.with(batch)
                .update(UNPROCESSED_TRANSACTIONS)
                .set(UNPROCESSED_TRANSACTIONS.LEASE_TOKEN, workerToken)
                .set(UNPROCESSED_TRANSACTIONS.LEASED_UNTIL, leaseUntil)
                .set(UNPROCESSED_TRANSACTIONS.UPDATED_AT, now)
                .from(batch)
                .where(UNPROCESSED_TRANSACTIONS.ID.eq(batch.field(UNPROCESSED_TRANSACTIONS.ID)))
                .returning()
                .fetch(mapper);

        // 3. Preserve deterministic ordering in memory (since UPDATE FROM does not guarantee result order)
        acquired.sort(Comparator.comparing(UnprocessedTransaction::getDateTime).thenComparing(UnprocessedTransaction::getId));
        return acquired;
    }

    @Override
    public boolean hasUnresolvedTransactions(AccountNumber accountFrom, ExpenseCategory category, YearMonth yearMonth) {
        OffsetDateTime startOfMonth = DateTimeUtils.startOfMonth(yearMonth);
        OffsetDateTime startOfNextMonth = DateTimeUtils.startOfNextMonth(yearMonth);

        return dsl.fetchExists(
                dsl.selectOne()
                        .from(UNPROCESSED_TRANSACTIONS)
                        .where(UNPROCESSED_TRANSACTIONS.ACCOUNT_FROM.eq(accountFrom.value()))
                        .and(UNPROCESSED_TRANSACTIONS.EXPENSE_CATEGORY.eq(category.getCode()))
                        .and(UNPROCESSED_TRANSACTIONS.STATUS.in("PENDING", "FAILED"))
                        .and(UNPROCESSED_TRANSACTIONS.DATETIME.ge(startOfMonth))
                        .and(UNPROCESSED_TRANSACTIONS.DATETIME.lt(startOfNextMonth))
        );
    }

    @Override
    public void update(UnprocessedTransaction transaction) {
        dsl.update(UNPROCESSED_TRANSACTIONS)
                .set(UNPROCESSED_TRANSACTIONS.STATUS, transaction.getStatus().name())
                .set(UNPROCESSED_TRANSACTIONS.RETRY_COUNT, transaction.getRetryCount())
                .set(UNPROCESSED_TRANSACTIONS.FAILURE_REASON, transaction.getFailureReason())
                .set(UNPROCESSED_TRANSACTIONS.LEASE_TOKEN, transaction.getLeaseToken())
                .set(UNPROCESSED_TRANSACTIONS.LEASED_UNTIL, transaction.getLeasedUntil())
                .set(UNPROCESSED_TRANSACTIONS.UPDATED_AT, transaction.getUpdatedAt())
                .where(UNPROCESSED_TRANSACTIONS.ID.eq(transaction.getId()))
                .execute();
    }
}
