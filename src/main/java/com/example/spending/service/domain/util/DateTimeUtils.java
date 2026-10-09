package com.example.spending.service.domain.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Utility class for normalizing dates and times across different client time zones
 * to the service's base time zone (UTC).
 * Encapsulates calculation of transaction dates, billing month periods, and month boundaries.
 */
public final class DateTimeUtils {

    /**
     * Base time zone for all transaction processing, month boundary definitions.
     */
    public static final ZoneId BASE_ZONE = ZoneOffset.UTC;

    private DateTimeUtils() {
        // utility class
    }

    /**
     * Converts the given OffsetDateTime to the base service time zone (UTC).
     */
    public static OffsetDateTime toBaseZone(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZoneSameInstant(BASE_ZONE).toOffsetDateTime();
    }

    /**
     * Extracts the LocalDate corresponding to the transaction instant in the base time zone (UTC).
     * Used for daily exchange rate lookups.
     *
     * @param dateTime transaction datetime with client timezone offset
     * @return LocalDate in base time zone (UTC)
     */
    public static LocalDate toBaseLocalDate(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZoneSameInstant(BASE_ZONE).toLocalDate();
    }

    /**
     * Extracts the YearMonth corresponding to the transaction instant in the base time zone (UTC).
     * Used for monthly spending accumulation and limit evaluation.
     *
     * @param dateTime transaction datetime with client timezone offset
     * @return YearMonth in base time zone (UTC)
     */
    public static YearMonth toBaseYearMonth(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return YearMonth.from(dateTime.atZoneSameInstant(BASE_ZONE));
    }

    /**
     * Calculates the start of the month (inclusive) in the base time zone (UTC).
     *
     * @param yearMonth the month and year
     * @return start of the month at 00:00:00 UTC
     */
    public static OffsetDateTime startOfMonth(YearMonth yearMonth) {
        Objects.requireNonNull(yearMonth, "YearMonth must not be null");
        return yearMonth.atDay(1).atStartOfDay(BASE_ZONE).toOffsetDateTime();
    }

    /**
     * Calculates the start of the next month (exclusive) in the base time zone (UTC).
     *
     * @param yearMonth the month and year
     * @return start of the next month at 00:00:00 UTC
     */
    public static OffsetDateTime startOfNextMonth(YearMonth yearMonth) {
        Objects.requireNonNull(yearMonth, "YearMonth must not be null");
        return yearMonth.plusMonths(1).atDay(1).atStartOfDay(BASE_ZONE).toOffsetDateTime();
    }
}
