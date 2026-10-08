package com.example.spending.service.infrastructure.mapper;

import com.example.spending.service.domain.model.ExchangeRate;
import com.example.spending.service.infrastructure.jooq.tables.records.ExchangeRatesRecord;
import org.jooq.RecordMapper;
import org.springframework.stereotype.Component;

@Component
public class ExchangeRateMapper implements RecordMapper<ExchangeRatesRecord, ExchangeRate> {

    @Override
    public ExchangeRate map(ExchangeRatesRecord record) {
        if (record == null) {
            return null;
        }
        return ExchangeRate.builder()
                .id(record.getId())
                .currencyPair(record.getCurrencyPair())
                .rateDate(record.getRateDate())
                .closeRate(record.getCloseRate())
                .previousCloseRate(record.getPreviousCloseRate())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
