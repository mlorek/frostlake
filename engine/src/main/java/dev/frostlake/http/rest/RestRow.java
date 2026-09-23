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

package dev.frostlake.http.rest;

import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One row of a SHOW or DESCRIBE answer, read by column name — case-insensitively, since listings name their
 * columns in lower case and scalar queries in upper case — with the conversions a REST body needs: flags that
 * listings spell {@code Y}/{@code N} or {@code true}/{@code false} as booleans, counts as integers and instants as
 * ISO-8601 date-times.
 */
public final class RestRow {

    private final ResultSet set;
    private final Row row;

    /**
     * @param set the result the row belongs to, which names its columns
     * @param row the row
     */
    public RestRow(final ResultSet set, final Row row) {
        this.set = set;
        this.row = row;
    }

    private int index(final String column) {
        int i = 0;
        for (final ResultSetColumn c : set.getColumns()) {
            if (c.getName().equalsIgnoreCase(column)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    /** Whether the answer has a column of that name. */
    public boolean has(final String column) {
        return index(column) >= 0;
    }

    /** The raw value, or null when it is NULL or the answer has no such column. */
    public Object get(final String column) {
        final int i = index(column);
        return i < 0 || i >= row.size() ? null : row.getValue(i);
    }

    /** The value as text; an instant in ISO-8601. Null for NULL and for a missing column. */
    public String string(final String column) {
        final Object value = get(column);
        if (value == null) {
            return null;
        }
        final String instant = instant(value);
        return instant != null ? instant : value.toString();
    }

    /** The value as text, or null when it is NULL, missing or empty. */
    public String nonEmpty(final String column) {
        final String value = string(column);
        return value == null || value.isEmpty() ? null : value;
    }

    /** The value as an integer, or null when it is NULL, missing, empty or not a number. */
    public Long integer(final String column) {
        final Object value = get(column);
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        if (value instanceof String) {
            final String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return Long.valueOf(new BigDecimal(text).longValue());
            } catch (final NumberFormatException notNumeric) {
                return null;
            }
        }
        return null;
    }

    /**
     * The value as a boolean: {@code Y}/{@code N}, {@code true}/{@code false} and {@code ON}/{@code OFF} in any
     * case. Null when it is NULL, missing, empty or none of those.
     */
    public Boolean bool(final String column) {
        final Object value = get(column);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return null;
        }
        final String text = value.toString().trim().toUpperCase(Locale.ROOT);
        if ("Y".equals(text) || "TRUE".equals(text) || "ON".equals(text) || "YES".equals(text)) {
            return Boolean.TRUE;
        }
        if ("N".equals(text) || "FALSE".equals(text) || "OFF".equals(text) || "NO".equals(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** The value as an ISO-8601 date-time, or null when it is NULL, missing or empty. */
    public String timestamp(final String column) {
        final Object value = get(column);
        if (value == null) {
            return null;
        }
        final String instant = instant(value);
        if (instant != null) {
            return instant;
        }
        final String text = value.toString();
        return text.isEmpty() ? null : text;
    }

    private static String instant(final Object value) {
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (value instanceof ZonedDateTime) {
            return ((ZonedDateTime) value).toOffsetDateTime().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        if (value instanceof LocalDateTime) {
            return ((LocalDateTime) value).atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }
        return null;
    }
}
