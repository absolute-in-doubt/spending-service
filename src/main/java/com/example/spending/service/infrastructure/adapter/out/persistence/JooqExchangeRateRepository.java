package com.example.spending.service.infrastructure.adapter.out.persistence;

import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.domain.repository.ExchangeRateRepository;
import com.example.spending.service.infrastructure.adapter.out.persistence.mapper.ExchangeRateMapper;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

import static com.example.spending.service.infrastructure.jooq.Tables.EXCHANGE_RATES;

@Repository
@RequiredArgsConstructor
public class JooqExchangeRateRepository implements ExchangeRateRepository {

    private final DSLContext dsl;
    private final ExchangeRateMapper mapper;

    @Override
    public void saveOrUpdate(ExchangeRate rate) {
        dsl.insertInto(EXCHANGE_RATES)
                .set(EXCHANGE_RATES.CURRENCY_PAIR, rate.getCurrencyPair())
                .set(EXCHANGE_RATES.RATE_DATE, rate.getRateDate())
                .set(EXCHANGE_RATES.CLOSE_RATE, rate.getCloseRate())
                .set(EXCHANGE_RATES.PREVIOUS_CLOSE_RATE, rate.getPreviousCloseRate())
                .onConflict(EXCHANGE_RATES.CURRENCY_PAIR, EXCHANGE_RATES.RATE_DATE)
                .doUpdate()
                .set(EXCHANGE_RATES.CLOSE_RATE, rate.getCloseRate())
                .set(EXCHANGE_RATES.PREVIOUS_CLOSE_RATE, rate.getPreviousCloseRate())
                .execute();
    }

    @Override
    public Optional<ExchangeRate> findByPairAndDate(String currencyPair, LocalDate date) {
        return dsl.selectFrom(EXCHANGE_RATES)
                .where(EXCHANGE_RATES.CURRENCY_PAIR.eq(currencyPair))
                .and(EXCHANGE_RATES.RATE_DATE.eq(date))
                .fetchOptional(mapper);
    }

    @Override
    public Optional<ExchangeRate> findEffectiveRate(String currencyPair, LocalDate date) {
        return dsl.selectFrom(EXCHANGE_RATES)
                .where(EXCHANGE_RATES.CURRENCY_PAIR.eq(currencyPair))
                .and(EXCHANGE_RATES.RATE_DATE.le(date))
                .orderBy(EXCHANGE_RATES.RATE_DATE.desc())
                .limit(1)
                .fetchOptional(mapper);
    }
}
