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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.DecimalText;
import dev.frostlake.values.BinaryValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The kinds of correlation key a hash settles, where equality is the value's own and no conversion takes part:
 * numbers, compared numerically and scale-insensitively ({@code 5}, {@code 5L} and {@code 5.0} are one key), texts by
 * their characters, DATE, TIME, TIMESTAMP_NTZ and BOOLEAN values by their values, TIMESTAMP_LTZ and TIMESTAMP_TZ
 * values by the instant they name, whatever offset spells it, and BINARY values by their bytes. Two keys of
 * different kinds compare by converting one, so a column whose members are of more than one kind, or of another
 * kind, is left to the comparison itself.
 */
final class CorrelationKeys {

    private CorrelationKeys() {
    }

    /**
     * A value's kind as a hash compares it, or null for a value of no such kind.
     *
     * @param value the value, not NULL
     * @return the kind, or null
     */
    static String kindOf(final Object value) {
        if (value instanceof Number) {
            return numericKey(value) == null ? null : "NUMBER";
        }
        if (value instanceof String) {
            return "TEXT";
        }
        if (value instanceof LocalDate) {
            return "DATE";
        }
        if (value instanceof LocalDateTime) {
            return "TIMESTAMP";
        }
        if (value instanceof LocalTime) {
            return "TIME";
        }
        if (value instanceof OffsetDateTime || value instanceof ZonedDateTime) {
            return "INSTANT";
        }
        if (value instanceof BinaryValue) {
            return "BINARY";
        }
        return value instanceof Boolean ? "BOOLEAN" : null;
    }

    /**
     * A key's canonical form: equal keys of one kind have one form.
     *
     * @param values the key's values, each of a kind {@link #kindOf} names
     * @return the form
     */
    static List<String> canonical(final List<Object> values) {
        final List<String> form = new ArrayList<>(values.size());
        for (final Object value : values) {
            form.add(formOf(value));
        }
        return form;
    }

    /** One value's canonical form: a number's numeric key, an instant's UTC spelling, a binary's hex digits. */
    private static String formOf(final Object value) {
        if (value instanceof Number) {
            return numericKey(value);
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toInstant().toString();
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toInstant().toString();
        }
        return value instanceof BinaryValue ? ((BinaryValue) value).toHex() : value.toString();
    }

    /** Canonical key for numeric equality, or null for values BigDecimal can't parse (NaN/Infinity). */
    private static String numericKey(final Object number) {
        try {
            return DecimalText.parse(number.toString()).stripTrailingZeros().toPlainString();
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
