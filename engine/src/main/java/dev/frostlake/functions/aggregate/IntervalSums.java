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

package dev.frostlake.functions.aggregate;

import dev.frostlake.values.DayTimeInterval;

import java.math.BigDecimal;

/**
 * SUM and AVG over day-time intervals, which add as intervals and answer one: over a day and an hour,
 * six tenths of a second and minus two and a half days SUM is {@code -1 11:29:59.400000000} and AVG
 * {@code -0 11:49:59.800000000}, the average truncated toward zero at the nanosecond — two seconds over three
 * rows average {@code .666666666}, either sign (live-verified).
 */
final class IntervalSums {

    private IntervalSums() {
    }

    /**
     * @param values the aggregated values
     * @return whether any of them is a day-time interval, which puts the whole aggregate on the interval path
     */
    static boolean holdsIntervals(final Iterable<Object> values) {
        for (final Object value : values) {
            if (value instanceof DayTimeInterval) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param values the aggregated values, intervals and NULLs
     * @return their sum, or null when none is present
     */
    static DayTimeInterval sum(final Iterable<Object> values) {
        DayTimeInterval total = null;
        for (final Object value : values) {
            if (value instanceof DayTimeInterval) {
                total = total == null ? DayTimeInterval.ofSeconds(((DayTimeInterval) value).seconds())
                    : total.plus((DayTimeInterval) value);
            }
        }
        return total;
    }

    /**
     * @param values the aggregated values, intervals and NULLs
     * @return their average, or null when none is present
     */
    static DayTimeInterval average(final Iterable<Object> values) {
        final DayTimeInterval total = sum(values);
        if (total == null) {
            return null;
        }
        long count = 0;
        for (final Object value : values) {
            if (value instanceof DayTimeInterval) {
                count++;
            }
        }
        return total.dividedBy(BigDecimal.valueOf(count));
    }
}
