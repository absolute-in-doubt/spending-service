package com.example.spending.service.domain.model;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Objects;

/**
 * Value Object representing the calendar monthly billing period in a specific time zone.
 * Encapsulates the month boundary calculations for transaction limit checks.
 */
public record BillingPeriod(YearMonth yearMonth, OffsetDateTime startInclusive, OffsetDateTime endExclusive) {

    public BillingPeriod {
        Objects.requireNonNull(yearMonth, "YearMonth must not be null");
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endExclusive, "endExclusive must not be null");
    }

    public static BillingPeriod of(OffsetDateTime dateTime, ZoneId baseZone) {
        Objects.requireNonNull(dateTime, "DateTime must not be null");
        Objects.requireNonNull(baseZone, "BaseZone must not be null");

        OffsetDateTime inZone = dateTime.atZoneSameInstant(baseZone).toOffsetDateTime();
        YearMonth ym = YearMonth.from(inZone);
        OffsetDateTime start = inZone.with(TemporalAdjusters.firstDayOfMonth())
                .toLocalDate().atStartOfDay(baseZone).toOffsetDateTime();
        OffsetDateTime end = inZone.with(TemporalAdjusters.firstDayOfNextMonth())
                .toLocalDate().atStartOfDay(baseZone).toOffsetDateTime();
        return new BillingPeriod(ym, start, end);
    }

    public static BillingPeriod ofUtc(OffsetDateTime dateTime) {
        return of(dateTime, ZoneOffset.UTC);
    }

    public boolean contains(OffsetDateTime dateTime, ZoneId baseZone) {
        OffsetDateTime inZone = dateTime.atZoneSameInstant(baseZone).toOffsetDateTime();
        return !inZone.isBefore(startInclusive) && inZone.isBefore(endExclusive);
    }
}
