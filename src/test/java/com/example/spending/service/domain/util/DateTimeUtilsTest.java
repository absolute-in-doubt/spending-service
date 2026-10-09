package com.example.spending.service.domain.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateTimeUtilsTest {

    @Test
    @DisplayName("Should normalize transaction with negative offset crossing into next month in UTC")
    void shouldNormalizeNegativeOffsetCrossingIntoNextMonthInUtc() {
        // 2022-01-31 23:30:00 in -05:00 is 2022-02-01 04:30:00 UTC
        OffsetDateTime dt = OffsetDateTime.of(2022, 1, 31, 23, 30, 0, 0, ZoneOffset.ofHours(-5));

        LocalDate baseDate = DateTimeUtils.toBaseLocalDate(dt);
        YearMonth baseYearMonth = DateTimeUtils.toBaseYearMonth(dt);
        OffsetDateTime baseZone = DateTimeUtils.toBaseZone(dt);

        assertThat(baseDate).isEqualTo(LocalDate.of(2022, 2, 1));
        assertThat(baseYearMonth).isEqualTo(YearMonth.of(2022, 2));
        assertThat(baseZone).isEqualTo(OffsetDateTime.of(2022, 2, 1, 4, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should normalize transaction with positive offset crossing into previous month in UTC")
    void shouldNormalizePositiveOffsetCrossingIntoPreviousMonthInUtc() {
        // 2022-02-01 01:30:00 in +03:00 is 2022-01-31 22:30:00 UTC
        OffsetDateTime dt = OffsetDateTime.of(2022, 2, 1, 1, 30, 0, 0, ZoneOffset.ofHours(3));

        LocalDate baseDate = DateTimeUtils.toBaseLocalDate(dt);
        YearMonth baseYearMonth = DateTimeUtils.toBaseYearMonth(dt);
        OffsetDateTime baseZone = DateTimeUtils.toBaseZone(dt);

        assertThat(baseDate).isEqualTo(LocalDate.of(2022, 1, 31));
        assertThat(baseYearMonth).isEqualTo(YearMonth.of(2022, 1));
        assertThat(baseZone).isEqualTo(OffsetDateTime.of(2022, 1, 31, 22, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should preserve exact date for UTC transactions")
    void shouldPreserveExactDateForUtcTransactions() {
        OffsetDateTime dt = OffsetDateTime.of(2022, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC);

        assertThat(DateTimeUtils.toBaseLocalDate(dt)).isEqualTo(LocalDate.of(2022, 1, 15));
        assertThat(DateTimeUtils.toBaseYearMonth(dt)).isEqualTo(YearMonth.of(2022, 1));
    }

    @Test
    @DisplayName("Should correctly compute month boundaries in UTC")
    void shouldComputeMonthBoundariesInUtc() {
        YearMonth ym = YearMonth.of(2022, 1);

        OffsetDateTime start = DateTimeUtils.startOfMonth(ym);
        OffsetDateTime end = DateTimeUtils.startOfNextMonth(ym);

        assertThat(start).isEqualTo(OffsetDateTime.of(2022, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        assertThat(end).isEqualTo(OffsetDateTime.of(2022, 2, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should handle null inputs safely")
    void shouldHandleNullInputsSafely() {
        assertThat(DateTimeUtils.toBaseLocalDate(null)).isNull();
        assertThat(DateTimeUtils.toBaseYearMonth(null)).isNull();
        assertThat(DateTimeUtils.toBaseZone(null)).isNull();

        assertThatThrownBy(() -> DateTimeUtils.startOfMonth(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DateTimeUtils.startOfNextMonth(null))
                .isInstanceOf(NullPointerException.class);
    }
}
