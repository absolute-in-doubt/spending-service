package com.example.spending.service.domain.model;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ExchangeRate {
    private Long id;
    private String currencyPair;
    private LocalDate rateDate;
    private BigDecimal closeRate;
    private BigDecimal previousCloseRate;
    private OffsetDateTime createdAt;

    public BigDecimal getEffectiveRate() {
        return closeRate != null ? closeRate : previousCloseRate;
    }
}
