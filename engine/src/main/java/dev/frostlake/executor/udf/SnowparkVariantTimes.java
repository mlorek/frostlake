/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.executor.udf;

import dev.frostlake.executor.SessionZone;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * The texts a handler's {@code Variant} holds for a date, a time and a timestamp, and the values read back from
 * such texts. A handler runs in the session's time zone (live-verified): the text of a {@code java.sql} value
 * is its instant's wall clock in that zone - so a DATE argument, an instant at UTC midnight, reads as the day
 * before in a zone behind UTC - and a text is read back as a wall clock of that zone. The texts and their
 * refusals are the {@code java.sql} classes' own.
 */
public final class SnowparkVariantTimes {

    private static final DateTimeFormatter DATE_TEXT = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final DateTimeFormatter TIME_TEXT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter TIMESTAMP_TEXT = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss");

    private static final String TIMESTAMP_FORMAT = "Timestamp format must be yyyy-mm-dd hh:mm:ss[.fffffffff]";
    private static final int YEAR_LENGTH = 4;
    private static final int FIELD_LENGTH = 2;
    private static final int MAX_MONTH = 12;
    private static final int MAX_DAY = 31;
    private static final int NANO_DIGITS = 9;

    /** Static helpers only — never instantiated. */
    private SnowparkVariantTimes() {
    }

    /**
     * @param date a date
     * @return its text, {@code 2024-03-05}
     */
    public static String text(final Date date) {
        return DATE_TEXT.format(Instant.ofEpochMilli(date.getTime()).atZone(SessionZone.current()));
    }

    /**
     * @param time a time
     * @return its text to the second, {@code 10:11:12}
     */
    public static String text(final Time time) {
        return TIME_TEXT.format(Instant.ofEpochMilli(time.getTime()).atZone(SessionZone.current()));
    }

    /**
     * @param timestamp a timestamp
     * @return its text with the fraction's trailing zeros dropped down to one digit, {@code 2024-03-05 10:11:12.0}
     */
    public static String text(final Timestamp timestamp) {
        final StringBuilder text = new StringBuilder(
            TIMESTAMP_TEXT.format(timestamp.toInstant().atZone(SessionZone.current())));
        final StringBuilder fraction = new StringBuilder(String.valueOf(timestamp.getNanos()));
        while (fraction.length() < NANO_DIGITS) {
            fraction.insert(0, '0');
        }
        int kept = fraction.length();
        while (kept > 1 && fraction.charAt(kept - 1) == '0') {
            kept--;
        }
        return text.append('.').append(fraction, 0, kept).toString();
    }

    /**
     * @param text a text
     * @return the date it spells as {@code yyyy-[m]m-[d]d}, at the zone's midnight
     */
    public static Date date(final String text) {
        if (text == null) {
            throw new IllegalArgumentException();
        }
        final int firstDash = text.indexOf('-');
        final int secondDash = text.indexOf('-', firstDash + 1);
        final int length = text.length();
        if (firstDash != YEAR_LENGTH || secondDash <= 0 || secondDash >= length - 1
                || secondDash - firstDash <= 1 || secondDash - firstDash > FIELD_LENGTH + 1
                || length - secondDash > FIELD_LENGTH + 1) {
            throw new IllegalArgumentException();
        }
        final int year = Integer.parseInt(text, 0, firstDash, 10);
        final int month = Integer.parseInt(text, firstDash + 1, secondDash, 10);
        final int day = Integer.parseInt(text, secondDash + 1, length, 10);
        if (month < 1 || month > MAX_MONTH || day < 1 || day > MAX_DAY) {
            throw new IllegalArgumentException();
        }
        return new Date(day(year, month, day).atStartOfDay(SessionZone.current()).toInstant().toEpochMilli());
    }

    /**
     * @param text a text
     * @return the time it spells as {@code hh:mm:ss}, on the first day the zone counts from
     */
    public static Time time(final String text) {
        if (text == null) {
            throw new IllegalArgumentException();
        }
        final int firstColon = text.indexOf(':');
        final int secondColon = text.indexOf(':', firstColon + 1);
        final int length = text.length();
        if (firstColon <= 0 || secondColon <= 0 || secondColon >= length - 1) {
            throw new IllegalArgumentException();
        }
        final int hour = Integer.parseInt(text, 0, firstColon, 10);
        final int minute = Integer.parseInt(text, firstColon + 1, secondColon, 10);
        final int second = Integer.parseInt(text, secondColon + 1, length, 10);
        return new Time(at(LocalDate.of(1970, 1, 1).atStartOfDay(), hour, minute, second, SessionZone.current())
            .toEpochMilli());
    }

    /**
     * @param written a text
     * @return the timestamp it spells as {@code yyyy-mm-dd hh:mm:ss[.fffffffff]}, a wall clock of the zone
     */
    public static Timestamp timestamp(final String written) {
        if (written == null) {
            throw new IllegalArgumentException("null string");
        }
        final String text = written.trim();
        final int dividingSpace = text.indexOf(' ');
        if (dividingSpace < 0) {
            throw new IllegalArgumentException(TIMESTAMP_FORMAT);
        }
        final int firstDash = text.indexOf('-');
        final int secondDash = text.indexOf('-', firstDash + 1);
        final int firstColon = text.indexOf(':', dividingSpace + 1);
        final int secondColon = text.indexOf(':', firstColon + 1);
        final int period = text.indexOf('.', secondColon + 1);
        if (firstDash != YEAR_LENGTH || secondDash <= 0 || secondDash >= dividingSpace - 1
                || secondDash - firstDash <= 1 || secondDash - firstDash > FIELD_LENGTH + 1
                || dividingSpace - secondDash <= 1 || dividingSpace - secondDash > FIELD_LENGTH + 1) {
            throw new IllegalArgumentException(TIMESTAMP_FORMAT);
        }
        final int year = Integer.parseInt(text, 0, firstDash, 10);
        final int month = Integer.parseInt(text, firstDash + 1, secondDash, 10);
        final int day = Integer.parseInt(text, secondDash + 1, dividingSpace, 10);
        if (month < 1 || month > MAX_MONTH || day < 1 || day > MAX_DAY) {
            throw new IllegalArgumentException(TIMESTAMP_FORMAT);
        }
        final int length = text.length();
        if (firstColon <= 0 || secondColon <= 0 || secondColon >= length - 1) {
            throw new IllegalArgumentException(TIMESTAMP_FORMAT);
        }
        final int hour = Integer.parseInt(text, dividingSpace + 1, firstColon, 10);
        final int minute = Integer.parseInt(text, firstColon + 1, secondColon, 10);
        final int second;
        int nanos = 0;
        if (period > 0 && period < length - 1) {
            second = Integer.parseInt(text, secondColon + 1, period, 10);
            int digits = length - (period + 1);
            if (digits > NANO_DIGITS || !Character.isDigit(text.charAt(period + 1))) {
                throw new IllegalArgumentException(TIMESTAMP_FORMAT);
            }
            nanos = Integer.parseInt(text, period + 1, length, 10);
            while (digits < NANO_DIGITS) {
                nanos *= 10;
                digits++;
            }
        } else if (period > 0) {
            throw new IllegalArgumentException(TIMESTAMP_FORMAT);
        } else {
            second = Integer.parseInt(text, secondColon + 1, length, 10);
        }
        final Timestamp timestamp = new Timestamp(
            at(day(year, month, day).atStartOfDay(), hour, minute, second, SessionZone.current()).toEpochMilli());
        timestamp.setNanos(nanos);
        return timestamp;
    }

    /** A day of a month, one past the month's end counted on into the next. */
    private static LocalDate day(final int year, final int month, final int day) {
        return LocalDate.of(year, month, 1).plusDays(day - 1L);
    }

    /** A wall clock of a zone as its instant, hours, minutes and seconds past their ranges counted on. */
    private static Instant at(final LocalDateTime midnight, final int hour, final int minute, final int second,
                              final ZoneId zone) {
        return midnight.plusHours(hour).plusMinutes(minute).plusSeconds(second).atZone(zone).toInstant();
    }
}
