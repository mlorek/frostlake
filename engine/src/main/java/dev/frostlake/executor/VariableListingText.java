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

package dev.frostlake.executor;

import dev.frostlake.values.BinaryValue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * How SHOW VARIABLES lists a variable: its value's family, in lower case, and its value as text (all live-verified).
 *
 * <pre>
 *   the variable holds    type             its value listed as
 *   a number              fixed            1.5
 *   a FLOAT               real             1.5
 *   a DECFLOAT            decfloat         1.5
 *   a Boolean             boolean          TRUE
 *   a text, or NULL       text             abc
 *   a binary value        binary           AB, its hex digits
 *   a date                date             2024-01-02
 *   a time                time             10:00:00.123000000, to the nanosecond whatever its precision
 *   a TIMESTAMP_NTZ       timestamp_ntz    2024-01-02 03:04:05.000000000Z, its wall clock
 *   a TIMESTAMP_LTZ       timestamp_ltz    2024-01-02 11:04:05.123000000Z, its instant in UTC
 *   a TIMESTAMP_TZ        timestamp_tz     2024-01-02 02:04:05.000000000Ztz=1500, its instant in UTC and its offset
 *                                          in minutes, 1440 added
 * </pre>
 */
final class VariableListingText {

    /** What the listing adds to a TIMESTAMP_TZ's offset in minutes, so that a negative offset lists positive. */
    private static final int OFFSET_BASE_MINUTES = 1440;

    private static final int SECONDS_PER_MINUTE = 60;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSSSS", Locale.ROOT);

    private static final DateTimeFormatter STAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS", Locale.ROOT);

    private VariableListingText() {
    }

    /**
     * The family a variable's value is listed under.
     *
     * @param value    the value, or null
     * @param decfloat whether a SET gave the variable a DECFLOAT
     * @return the family's word
     */
    static String type(final Object value, final boolean decfloat) {
        if (decfloat) {
            return "decfloat";
        }
        if (value instanceof Double || value instanceof Float) {
            return "real";
        }
        if (value instanceof Number) {
            return "fixed";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof BinaryValue) {
            return "binary";
        }
        if (value instanceof LocalDate) {
            return "date";
        }
        if (value instanceof LocalTime) {
            return "time";
        }
        if (value instanceof LocalDateTime) {
            return "timestamp_ntz";
        }
        if (value instanceof OffsetDateTime) {
            return "timestamp_ltz";
        }
        return value instanceof ZonedDateTime ? "timestamp_tz" : "text";
    }

    /**
     * A variable's value as the listing spells it.
     *
     * @param value the value, or null
     * @return its text, or null for NULL
     */
    static String value(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "TRUE" : "FALSE";
        }
        if (value instanceof LocalTime) {
            return TIME.format((LocalTime) value);
        }
        if (value instanceof LocalDateTime) {
            return STAMP.format((LocalDateTime) value) + "Z";
        }
        if (value instanceof OffsetDateTime) {
            return STAMP.format(((OffsetDateTime) value).withOffsetSameInstant(ZoneOffset.UTC)) + "Z";
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime stamp = (ZonedDateTime) value;
            return STAMP.format(stamp.withZoneSameInstant(ZoneOffset.UTC)) + "Ztz="
                + (OFFSET_BASE_MINUTES + stamp.getOffset().getTotalSeconds() / SECONDS_PER_MINUTE);
        }
        return value.toString();
    }
}
