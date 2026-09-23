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

package dev.frostlake.task;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * An interval SCHEDULE of a task or an alert: a count and a unit, {@code '<n> SECOND[S]'}, {@code '<n> S'},
 * {@code '<n> MINUTE[S]'}, {@code '<n> M'}, {@code '<n> HOUR[S]'} or {@code '<n> H'}, the unit in any case. The
 * count and the unit are separated by exactly one whitespace character (a space, a tab, a line feed, a carriage
 * return, a form feed or a vertical tab); any run of them after the unit is allowed, one before the count is not.
 * The count is a whole number that fits a long, a leading {@code +} allowed. The schedule text is kept as written;
 * this reads its length.
 *
 * <p>The length is the count times the unit in milliseconds, in the account's own 64-bit arithmetic: a count of
 * seconds or minutes whose product passes the long range wraps around, so it may land on a negative length (an
 * invalid schedule) or on a small positive one, while a count of hours stops at the longest length a long holds.
 */
public final class IntervalSchedule {

    /** The shortest interval a schedule may name. */
    public static final long MINIMUM_SECONDS = 10L;

    /** The longest interval a schedule may name: 11,520 minutes, eight days. */
    public static final long MAXIMUM_SECONDS = 11_520L * 60L;

    /** The refusal of an interval shorter than {@link #MINIMUM_SECONDS}. */
    public static final String TOO_SHORT_MESSAGE = "Cannot set schedule less than 10 seconds.";

    /** The refusal of an interval longer than {@link #MAXIMUM_SECONDS}. */
    public static final String TOO_LONG_MESSAGE = "Cannot set schedule greater than 11,520 minutes.";

    private IntervalSchedule() {
    }

    /**
     * The interval a schedule names, in milliseconds.
     *
     * @param schedule the schedule's text
     * @return the interval in milliseconds, zero or negative for a count below one or a product that wrapped; null
     *         when the text is no interval schedule
     */
    public static Long millis(final String schedule) {
        if (schedule == null) {
            return null;
        }
        // Splitting on one whitespace character keeps an empty word for a leading or a doubled one, and drops the
        // empty words trailing ones leave: exactly the forms the account refuses and accepts.
        final String[] words = schedule.split("\\s");
        if (words.length != 2) {
            return null;
        }
        final long count;
        try {
            count = Long.parseLong(words[0]);
        } catch (final NumberFormatException notACount) {
            return null;
        }
        switch (words[1].toUpperCase(Locale.ROOT)) {
            case "S":
            case "SECOND":
            case "SECONDS":
                return count * 1000L;
            case "M":
            case "MINUTE":
            case "MINUTES":
                return count * 60_000L;
            case "H":
            case "HOUR":
            case "HOURS":
                return TimeUnit.HOURS.toMillis(count);
            default:
                return null;
        }
    }

    /**
     * The interval a schedule names, in whole seconds.
     *
     * @param schedule the schedule's text
     * @return the interval in seconds; -1 when the text is no interval schedule; 0 when its length is not positive
     */
    public static long seconds(final String schedule) {
        final Long millis = millis(schedule);
        if (millis == null) {
            return -1L;
        }
        return millis <= 0L ? 0L : millis / 1000L;
    }
}
