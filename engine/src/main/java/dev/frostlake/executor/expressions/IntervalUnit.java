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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The unit of an {@link IntervalExpression}. One constant per UNIT, with the many spellings live
 * accepts for each resolved by {@link #fromSpelling}: {@code year}, {@code y}, {@code yy}, {@code yyyy},
 * {@code yr} and {@code years} are one unit, not six.
 *
 * <p>EVERY SPELLING HERE WAS READ OFF A LIVE ACCOUNT, and the list has holes no rule predicts. Adding
 * one because it looks like its neighbours is how this list goes wrong: a first draft guessed fifteen
 * from the pattern and FOUR were refused live —
 *
 * <pre>
 *   wk  accepted     wks      REFUSED
 *   sec accepted     ss       REFUSED
 *   woy accepted     wofy     REFUSED
 *   msec accepted    msecs    REFUSED
 *   usec accepted    usecs    REFUSED
 *   nanosecs accepted   nsecs REFUSED
 * </pre>
 *
 * <p>So do not "tidy" or extend this list. Measure the spelling, then add it.
 */
public enum IntervalUnit {
    YEAR,
    QUARTER,
    MONTH,
    WEEK,
    DAY,
    HOUR,
    MINUTE,
    SECOND,
    MILLISECOND,
    MICROSECOND,
    NANOSECOND;

    private static final Map<String, IntervalUnit> SPELLINGS = new HashMap<>();

    static {
        register(YEAR, "year", "years", "y", "yy", "yyyy", "yr", "yrs");
        register(QUARTER, "quarter", "quarters", "qtr", "qtrs", "q");
        register(MONTH, "month", "months", "mm", "mon", "mons");
        register(WEEK, "week", "weeks", "wk", "woy", "weekofyear");
        register(DAY, "day", "days", "dd", "d", "dayofmonth");
        register(HOUR, "hour", "hours", "hh", "hr", "hrs", "h");
        register(MINUTE, "minute", "minutes", "mi", "min", "mins", "m");
        register(SECOND, "second", "seconds", "sec", "secs", "s");
        register(MILLISECOND, "millisecond", "milliseconds", "ms", "msec");
        register(MICROSECOND, "microsecond", "microseconds", "us", "usec");
        register(NANOSECOND, "nanosecond", "nanoseconds", "ns", "nsec", "nanosec", "nanosecs");
    }

    private static void register(final IntervalUnit unit, final String... spellings) {
        for (final String spelling : spellings) {
            SPELLINGS.put(spelling, unit);
        }
    }

    /**
     * The unit a spelling names, or null when live does not recognise it either.
     *
     * @param text the unit word as written, in any case
     * @return the unit, or null when the spelling is not one live accepts
     */
    public static IntervalUnit fromSpelling(final String text) {
        return text == null ? null : SPELLINGS.get(text.toLowerCase(Locale.ROOT));
    }

    /**
     * Whether this unit measures a WHOLE DAY or more, which decides whether adding it to a DATE leaves
     * a DATE or promotes to TIMESTAMP_NTZ. DAY is the one unit whose answer also depends on which
     * INTERVAL spelling was used — see {@link IntervalExpression#isUnitInString}.
     *
     * @return true for year, quarter, month, week and day
     */
    public boolean isWholeDay() {
        return this == YEAR || this == QUARTER || this == MONTH || this == WEEK || this == DAY;
    }
}
