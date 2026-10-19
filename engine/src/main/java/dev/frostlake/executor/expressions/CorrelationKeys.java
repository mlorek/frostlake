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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The kinds of correlation key a hash settles, where equality is the value's own and no conversion takes part:
 * numbers, compared numerically and scale-insensitively ({@code 5}, {@code 5L} and {@code 5.0} are one key), texts by
 * their characters, and DATE, TIME, TIMESTAMP_NTZ and BOOLEAN values by their values. Two keys of different kinds
 * compare by converting one, so a column whose members are of more than one kind, or of another kind, is left to the
 * comparison itself.
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
            form.add(value instanceof Number ? numericKey(value) : value.toString());
        }
        return form;
    }

    /** Canonical key for numeric equality, or null for values BigDecimal can't parse (NaN/Infinity). */
    private static String numericKey(final Object number) {
        try {
            return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
