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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The week the session's WEEK_START and WEEK_OF_YEAR_POLICY parameters make (live-verified), for DAYOFWEEK, WEEK,
 * WEEKOFYEAR, YEAROFWEEK, DATE_TRUNC('WEEK'), DATE_PART, EXTRACT, LAST_DAY(…, 'WEEK') and DATEDIFF(WEEK, …); the ISO
 * variants do not follow it.
 *
 * <pre>
 *   WEEK_START 0 (the default)   weeks start on Monday; DAYOFWEEK counts Sunday 0 … Saturday 6
 *   WEEK_START 1 … 7             weeks start on that day, Monday 1 … Sunday 7; DAYOFWEEK counts 1 … 7 from it
 *   WEEK_OF_YEAR_POLICY 0        week 1 is the first week with four days of the year, as ISO counts, and a week
 *                                belongs to the year that holds its fourth day
 *   WEEK_OF_YEAR_POLICY 1        week 1 is the week holding January 1st of the date's own year
 * </pre>
 *
 * <p>Held in a ThreadLocal pinned for the statement, as {@link SessionZone} carries the zone, for the same reason:
 * one engine serves many sessions and the functions have no session to ask.
 */
public final class SessionWeek {

    private static final ThreadLocal<int[]> PINNED = new ThreadLocal<int[]>();

    private static final int DAYS_PER_WEEK = 7;

    private SessionWeek() {
    }

    /**
     * Pin this statement's week, handing back what was pinned before so the caller can restore it. A value that
     * is no whole number counts as the default.
     *
     * @param weekStart        the session's WEEK_START, or null
     * @param weekOfYearPolicy the session's WEEK_OF_YEAR_POLICY, or null
     * @return what was pinned before
     */
    public static int[] pin(final Object weekStart, final Object weekOfYearPolicy) {
        final int[] previous = PINNED.get();
        PINNED.set(new int[] {whole(weekStart, 0, DAYS_PER_WEEK), whole(weekOfYearPolicy, 0, 1)});
        return previous;
    }

    /**
     * Put back what {@link #pin} displaced.
     *
     * @param previous what it answered
     */
    public static void restore(final int[] previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    private static int whole(final Object value, final int least, final int most) {
        if (value == null) {
            return 0;
        }
        try {
            final int number = Integer.parseInt(value.toString().trim());
            return number < least || number > most ? 0 : number;
        } catch (final NumberFormatException notANumber) {
            return 0;
        }
    }

    private static int weekStart() {
        final int[] pinned = PINNED.get();
        return pinned == null ? 0 : pinned[0];
    }

    private static int weekOfYearPolicy() {
        final int[] pinned = PINNED.get();
        return pinned == null ? 0 : pinned[1];
    }

    /** The day a week starts on. */
    private static DayOfWeek firstDay() {
        final int start = weekStart();
        return start == 0 ? DayOfWeek.MONDAY : DayOfWeek.of(start);
    }

    /**
     * DAYOFWEEK: Sunday 0 to Saturday 6 under the default, else 1 to 7 counted from the day weeks start on.
     *
     * @param date the date
     * @return the day's number
     */
    public static int dayOfWeek(final LocalDate date) {
        final int start = weekStart();
        final int iso = date.getDayOfWeek().getValue();
        return start == 0 ? iso % DAYS_PER_WEEK : (iso - start + DAYS_PER_WEEK) % DAYS_PER_WEEK + 1;
    }

    /**
     * The first day of the week holding the date.
     *
     * @param date the date
     * @return the week's first day
     */
    public static LocalDate weekStartOf(final LocalDate date) {
        final int offset = (date.getDayOfWeek().getValue() - firstDay().getValue() + DAYS_PER_WEEK) % DAYS_PER_WEEK;
        return date.minusDays(offset);
    }

    /**
     * WEEK and WEEKOFYEAR: the date's week of its year.
     *
     * @param date the date
     * @return the week's number, from 1
     */
    public static int weekOfYear(final LocalDate date) {
        final LocalDate start = weekStartOf(date);
        final LocalDate first = weekOfYearPolicy() == 1 ? weekStartOf(date.withDayOfYear(1))
            : weekStartOf(LocalDate.of(yearOfWeek(date), 1, 4));
        return (int) (ChronoUnit.DAYS.between(first, start) / DAYS_PER_WEEK) + 1;
    }

    /**
     * YEAROFWEEK: the year the date's week belongs to.
     *
     * @param date the date
     * @return the year
     */
    public static int yearOfWeek(final LocalDate date) {
        return weekOfYearPolicy() == 1 ? date.getYear() : weekStartOf(date).plusDays(3).getYear();
    }

    /**
     * DATEDIFF(WEEK, …): how many week starts lie after the first date up to the second.
     *
     * @param start the first date
     * @param end   the second date
     * @return the count, negative when the second date is the earlier
     */
    public static long weeksBetween(final LocalDate start, final LocalDate end) {
        return ChronoUnit.DAYS.between(weekStartOf(start), weekStartOf(end)) / DAYS_PER_WEEK;
    }
}
