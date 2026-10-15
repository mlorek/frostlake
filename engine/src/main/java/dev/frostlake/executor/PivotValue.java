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

import dev.frostlake.parser.FrostlakeParser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One column of a PIVOT: the value its rows carry in the FOR column, and the name the column takes.
 *
 * <p>A row belongs to the column by VALUE, read as the FOR column's own kind of value: a BOOLEAN cell meets
 * {@code TRUE}, the text {@code 'true'} and the number {@code 1} alike, a NUMBER cell {@code 1.50} meets
 * {@code 1.5}, and a NULL cell meets the column written {@code NULL}. A written value names its column as the
 * statement wrote it, a keyword in upper case ({@code IN (true)} is {@code TRUE}); a value read from the rows —
 * {@code IN (ANY)}, a subquery — names it as the account prints that value: {@code TRUE}, {@code 1.5} with no
 * trailing zero, a text, a date and a timestamp between quotes, and {@code NULL} (live-verified).
 */
final class PivotValue {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSSSS");

    private final Object value;
    private final String name;

    PivotValue(final Object value, final String name) {
        this.value = value;
        this.name = name;
    }

    /**
     * A column for a value the IN list wrote.
     *
     * @param literal the value as written
     * @param value the value it spells, null for {@code NULL}
     * @param alias the alias written after it, or null
     * @return the column
     */
    static PivotValue written(final FrostlakeParser.LiteralContext literal, final Object value, final String alias) {
        if (alias != null) {
            return new PivotValue(value, alias);
        }
        final boolean keyword = literal.TRUE() != null || literal.FALSE() != null || literal.NULL() != null;
        return new PivotValue(value, keyword ? literal.getText().toUpperCase(Locale.ROOT) : literal.getText());
    }

    /**
     * A column for a value read from the rows.
     *
     * @param value the value, null for the rows that hold none
     * @return the column
     */
    static PivotValue read(final Object value) {
        return new PivotValue(value, nameOf(value));
    }

    String name() {
        return name;
    }

    /**
     * The column's name under the aggregate's alias, which it ends in — inside the quotes of a quoted name:
     * {@code SUM(amt) AS total} names the columns {@code 'JAN_TOTAL'} and, beside a value's own alias {@code j},
     * {@code J_TOTAL} (live-verified).
     *
     * @param aggregateAlias the aggregate's alias, or null when it has none
     * @return the name
     */
    String name(final String aggregateAlias) {
        if (aggregateAlias == null) {
            return name;
        }
        if (name.length() > 1 && name.startsWith("'") && name.endsWith("'")) {
            return name.substring(0, name.length() - 1) + "_" + aggregateAlias + "'";
        }
        return name + "_" + aggregateAlias;
    }

    Object value() {
        return value;
    }

    /**
     * @param cell a row's FOR-column value
     * @return whether the row belongs to this column
     */
    boolean holds(final Object cell) {
        if (cell == null || value == null) {
            return cell == null && value == null;
        }
        if (cell instanceof Boolean) {
            final Boolean mine = asBoolean(value);
            return mine != null && mine.equals(cell);
        }
        if (cell instanceof Number) {
            final BigDecimal mine = asNumber(value);
            final BigDecimal theirs = asNumber(cell);
            if (mine != null && theirs != null) {
                return mine.compareTo(theirs) == 0;
            }
        }
        return String.valueOf(value).equals(String.valueOf(cell));
    }

    private static String nameOf(final Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "TRUE" : "FALSE";
        }
        if (value instanceof Number) {
            final BigDecimal exact = asNumber(value);
            return exact == null ? String.valueOf(value) : exact.stripTrailingZeros().toPlainString();
        }
        if (value instanceof LocalDateTime) {
            return "'" + TIMESTAMP.format((LocalDateTime) value) + "Z'";
        }
        return "'" + value + "'";
    }

    private static Boolean asBoolean(final Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            final BigDecimal exact = asNumber(value);
            return exact == null ? null : Boolean.valueOf(exact.signum() != 0);
        }
        final String text = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        switch (text) {
            case "true":
            case "t":
            case "yes":
            case "y":
            case "on":
            case "1":
                return Boolean.TRUE;
            case "false":
            case "f":
            case "no":
            case "n":
            case "off":
            case "0":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private static BigDecimal asNumber(final Object value) {
        if (value instanceof Boolean) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (final NumberFormatException notANumber) {
            return null;
        }
    }
}
