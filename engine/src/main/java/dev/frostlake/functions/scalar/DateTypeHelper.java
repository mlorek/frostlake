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

package dev.frostlake.functions.scalar;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * Small shared helper for the date/time functions. The engine represents both DATE and TIMESTAMP values
 * as strings (and a few functions as {@link LocalDate} / {@link LocalDateTime}), so "is this value a pure
 * DATE?" cannot be answered by type alone — a date-only string ({@code 2023-01-31}) is a DATE while a
 * string with a time component is a TIMESTAMP. This decides so DATEADD / DATE_TRUNC can keep DATE inputs
 * a DATE for day-or-larger units instead of widening them to a timestamp.
 */
public final class DateTypeHelper {

    // Compiled once — this ran String.matches (a fresh Pattern compile) per call.
    private static final Pattern DASHED_DATE = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");

    private DateTypeHelper() {
    }

    /**
     * Whether {@code value} represents a pure DATE (no time-of-day): a {@link LocalDate}, or a string of
     * the form {@code yyyy-[m]m-[d]d} with no trailing time. A {@link LocalDateTime} or a string carrying
     * a time component is NOT date-only.
     */
    public static boolean isDateOnly(final Object value) {
        if (value instanceof LocalDate) {
            return true;
        }
        if (value instanceof LocalDateTime) {
            return false;
        }
        if (value instanceof String) {
            return DASHED_DATE.matcher(((String) value).trim()).matches();
        }
        return false;
    }
}
