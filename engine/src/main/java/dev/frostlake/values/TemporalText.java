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

package dev.frostlake.values;

import dev.frostlake.types.DataType;
import dev.frostlake.types.TypeCategory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * How {@code getString} renders a temporal cell, per DECLARED type — measured against a real account's
 * driver, which is the thing a caller compares Frostlake to:
 *
 * <pre>
 *   DATE            2026-08-07
 *   TIME            12:34:56                          no fractional part, ever
 *   TIMESTAMP_NTZ   2026-08-07 12:34:56.789
 *   TIMESTAMP_LTZ   2026-08-07 12:34:56.789 -0700
 *   TIMESTAMP_TZ    2026-08-07 12:34:56.789 -0700     same as LTZ
 * </pre>
 *
 * <p>Three details that only measuring gives you. <b>TIME drops its fraction entirely</b> — a
 * {@code TIME(9)} holding {@code 12:34:56.123456789} still reads {@code 12:34:56}. A timestamp always
 * shows <b>exactly three</b> fractional digits: zero-PADDED ({@code .1} reads {@code .100}) and
 * TRUNCATED ({@code .123456789} reads {@code .123}), never trimmed to significant digits. And the two
 * zoned types are indistinguishable here, both carrying a numeric {@code ±HHMM} offset.
 *
 * <p>Driven by the declared type rather than the Java class, because the same class serves several
 * types: a TIMESTAMP_LTZ cell and a TIMESTAMP_NTZ cell are both {@code LocalDateTime} in this engine,
 * and only the column knows which one wants an offset. A value that is already text passes straight
 * through, as does anything whose column is not temporal.
 */
public final class TemporalText {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter NAIVE =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter ZONED =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS Z");

    private TemporalText() {
    }

    /**
     * The value a JSON wire should carry for a column of {@code declared}: a temporal cell as its
     * rendered TEXT, and anything else UNTOUCHED.
     *
     * <p>Untouched matters. Rendering every cell would cross a number as the string {@code "42.0"},
     * and the far side would fail to read it as an int — the wire keeps JSON's own types for
     * everything this class has no opinion about.
     */
    public static Object wireValue(final Object value, final DataType declared) {
        if (value == null || declared == null || declared.getCategory() != TypeCategory.DATE_TIME) {
            return value;
        }
        return render(value, declared);
    }

    /**
     * {@code value} as {@code getString} should report it for a column of {@code declared}, or null
     * when the value is null. Anything this cannot place — a non-temporal column, a cell already
     * carrying text, a temporal class not modelled here — falls back to {@code toString}, which is
     * what every caller did before this existed.
     */
    public static String render(final Object value, final DataType declared) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || declared == null
                || declared.getCategory() != TypeCategory.DATE_TIME) {
            return value.toString();
        }
        final String type = declared.getName().toUpperCase();
        if ("DATE".equals(type)) {
            return format(value, DATE);
        }
        if ("TIME".equals(type)) {
            return format(value, TIME);
        }
        if ("TIMESTAMP_LTZ".equals(type) || "TIMESTAMP_TZ".equals(type)) {
            return zoned(value);
        }
        return format(value, NAIVE);
    }

    /** A zoned rendering: a value that carries no zone is read in the session's. */
    private static String zoned(final Object value) {
        if (value instanceof ZonedDateTime) {
            return ZONED.format((ZonedDateTime) value);
        }
        if (value instanceof OffsetDateTime) {
            return ZONED.format((OffsetDateTime) value);
        }
        if (value instanceof Instant) {
            return ZONED.format(((Instant) value).atZone(ZoneId.systemDefault()));
        }
        if (value instanceof LocalDateTime) {
            return ZONED.format(((LocalDateTime) value).atZone(ZoneId.systemDefault()));
        }
        if (value instanceof java.sql.Timestamp) {
            return ZONED.format(((java.sql.Timestamp) value).toLocalDateTime()
                .atZone(ZoneId.systemDefault()));
        }
        return value.toString();
    }

    /** A naive rendering, widening a date or a time to whatever the pattern needs. */
    private static String format(final Object value, final DateTimeFormatter formatter) {
        if (value instanceof LocalDateTime) {
            return formatter.format((LocalDateTime) value);
        }
        if (value instanceof LocalDate) {
            return formatter == DATE ? formatter.format((LocalDate) value)
                : formatter.format(((LocalDate) value).atStartOfDay());
        }
        if (value instanceof LocalTime) {
            return formatter == TIME ? formatter.format((LocalTime) value) : value.toString();
        }
        if (value instanceof ZonedDateTime) {
            return formatter.format(((ZonedDateTime) value).toLocalDateTime());
        }
        if (value instanceof OffsetDateTime) {
            return formatter.format(((OffsetDateTime) value).toLocalDateTime());
        }
        if (value instanceof Instant) {
            return formatter.format(((Instant) value).atZone(ZoneId.systemDefault()));
        }
        if (value instanceof java.sql.Timestamp) {
            return formatter.format(((java.sql.Timestamp) value).toLocalDateTime());
        }
        return value.toString();
    }
}
