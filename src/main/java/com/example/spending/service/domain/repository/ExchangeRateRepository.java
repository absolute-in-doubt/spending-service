package com.example.spending.service.domain.repository;

import com.example.spending.service.domain.model.ExchangeRate;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateRepository {

    void saveOrUpdate(ExchangeRate rate);

    Optional<ExchangeRate> findByPairAndDate(String currencyPair, LocalDate date);

    Optional<ExchangeRate> findEffectiveRate(String currencyPair, LocalDate date);
}
