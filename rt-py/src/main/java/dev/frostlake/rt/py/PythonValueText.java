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

package dev.frostlake.rt.py;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Python's {@code str()} of a temporal value, which is how Snowflake stores one inside a VARIANT a handler returns.
 * Kept apart from {@link PythonRuntime} so the CPython path never loads GraalPy just to format a date.
 */
public final class PythonValueText {

    private PythonValueText() {
    }

    /** {@code str(datetime)} — {@code 2024-10-16 19:47:11.488000}; no fraction when the microsecond is 0. */
    public static String pythonDatetimeStr(final LocalDateTime dt) {
        return String.format(Locale.ROOT, "%04d-%02d-%02d %s",
            dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), pythonTimeStr(dt.toLocalTime()));
    }

    /** {@code str(time)} — {@code 19:47:11.488000}; no fraction when the microsecond is 0. */
    public static String pythonTimeStr(final LocalTime t) {
        final int micro = t.getNano() / 1000;
        final String base = String.format(Locale.ROOT, "%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond());
        return micro == 0 ? base : base + String.format(Locale.ROOT, ".%06d", micro);
    }

    /** {@code str()} of an aware datetime — {@code 2024-01-02 03:04:05+05:00}. */
    public static String pythonDatetimeStr(final OffsetDateTime dt) {
        final int seconds = dt.getOffset().getTotalSeconds();
        final int abs = Math.abs(seconds);
        return pythonDatetimeStr(dt.toLocalDateTime()) + String.format(Locale.ROOT, "%s%02d:%02d",
            seconds < 0 ? "-" : "+", abs / 3600, abs / 60 % 60);
    }

    /**
     * A temporal nested INSIDE a returned container as the text Snowflake stores in a VARIANT: Python's
     * {@code str()} of the value, with the naive fields verbatim. A top-level return keeps its
     * {@code java.time} type instead — only container members are stringified.
     */
    public static Object temporalInContainer(final Object value) {
        if (value instanceof LocalDateTime) {
            return pythonDatetimeStr((LocalDateTime) value);
        }
        if (value instanceof LocalTime) {
            return pythonTimeStr((LocalTime) value);
        }
        if (value instanceof LocalDate) {
            return value.toString();
        }
        if (value instanceof ZonedDateTime) {
            return pythonDatetimeStr(((ZonedDateTime) value).toOffsetDateTime());
        }
        if (value instanceof OffsetDateTime) {
            return pythonDatetimeStr((OffsetDateTime) value);
        }
        return value;
    }
}
