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

package dev.frostlake.functions.scalar.datetime;

import dev.frostlake.executor.SessionZone;
import dev.frostlake.executor.expressions.IntervalUnit;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * DATEADD's move by a MINUTE or a finer part, which takes any 64-bit count, in the account's own
 * arithmetic (live-verified):
 *
 * <ul>
 *   <li>a count of minutes becomes seconds in 64 bits and wraps: {@code DATEADD(minute,
 *       9223372036854775807, ts)} is one minute before {@code ts};</li>
 *   <li>seconds are added to the timestamp's epoch second in 64 bits and wrap: from
 *       {@code 2024-01-15 10:00:00} the largest count lands on epoch second -9223372035149463009;</li>
 *   <li>milliseconds, microseconds and nanoseconds split into whole seconds and a fraction first, so
 *       they never wrap: the largest count of milliseconds is {@code 292279048-08-31 17:12:55.807}.</li>
 * </ul>
 *
 * <p>A moment past the years a wall clock holds is shown the way the account's renderer spells it: the
 * epoch second moved to the year 0 in 64 bits, the civil date of that day, and the year cut to 32 bits
 * ({@code 2147483647-12-31 23:59:59} is followed by {@code -2147483648-01-01 00:00:00}). That wall
 * clock is carried where it can be; where even it cannot, the shift is refused.
 *
 * <p>A TIME moves round its day in exact arithmetic, whatever the count.
 */
public final class FinePartShift {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_DAY = 86_400L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final long MILLIS_PER_SECOND = 1_000L;
    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;
    /** The seconds from 0000-01-01 to 1970-01-01, which the renderer adds before it splits off the day. */
    private static final long SECONDS_FROM_YEAR_ZERO = 62_167_219_200L;
    /** The days from 0000-03-01 to 1970-01-01, the era the civil-date algorithm counts from. */
    private static final long DAYS_FROM_MARCH_OF_YEAR_ZERO = 719_468L;
    private static final long DAYS_PER_ERA = 146_097L;
    private static final long YEARS_PER_ERA = 400L;
    private static final long LOWEST_EPOCH_SECOND = LocalDateTime.MIN.toEpochSecond(ZoneOffset.UTC);
    private static final long HIGHEST_EPOCH_SECOND = LocalDateTime.MAX.toEpochSecond(ZoneOffset.UTC);

    private FinePartShift() {
    }

    /**
     * Whether a part is one this shift moves by: a minute or finer.
     *
     * @param unit the part
     * @return whether it is fine
     */
    public static boolean isFine(final IntervalUnit unit) {
        return unit == IntervalUnit.MINUTE || unit == IntervalUnit.SECOND || unit == IntervalUnit.MILLISECOND
            || unit == IntervalUnit.MICROSECOND || unit == IntervalUnit.NANOSECOND;
    }

    /**
     * A temporal value moved by {@code count} of a fine part, in its own flavour: a TIME round its day, a
     * TIMESTAMP_TZ at its written offset, a TIMESTAMP_LTZ as an instant read in the session's zone, and
     * anything else as the wall clock {@code wallClock} it reads as.
     *
     * @param value the value as written
     * @param wallClock the value's wall clock, for a DATE or a TIMESTAMP_NTZ
     * @param unit a fine part
     * @param count how many of it, negative to move back
     * @return the moved value
     */
    public static Object shift(final Object value, final LocalDateTime wallClock, final IntervalUnit unit,
                               final long count) {
        if (value instanceof LocalTime) {
            return plus((LocalTime) value, unit, count);
        }
        if (value instanceof ZonedDateTime) {
            final ZonedDateTime written = (ZonedDateTime) value;
            return plus(written.toLocalDateTime(), unit, count).atZone(written.getOffset());
        }
        if (value instanceof OffsetDateTime) {
            final LocalDateTime utc = LocalDateTime.ofInstant(((OffsetDateTime) value).toInstant(), ZoneOffset.UTC);
            return plus(utc, unit, count).atOffset(ZoneOffset.UTC).atZoneSameInstant(SessionZone.current())
                .toOffsetDateTime();
        }
        return plus(wallClock, unit, count);
    }

    /**
     * A TIME moved round its day.
     *
     * @param time the time
     * @param unit a fine part
     * @param count how many of it
     * @return the moved time
     */
    static LocalTime plus(final LocalTime time, final IntervalUnit unit, final long count) {
        switch (unit) {
            case MINUTE:
                return time.plusMinutes(count);
            case SECOND:
                return time.plusSeconds(count);
            case MILLISECOND:
                return time.plusSeconds(count / MILLIS_PER_SECOND).plusNanos(count % MILLIS_PER_SECOND * NANOS_PER_MILLI);
            case MICROSECOND:
                return time.plusSeconds(count / MICROS_PER_SECOND).plusNanos(count % MICROS_PER_SECOND * NANOS_PER_MICRO);
            default:
                return time.plusNanos(count);
        }
    }

    /**
     * A wall clock moved by a fine part.
     *
     * @param wallClock the wall clock
     * @param unit a fine part
     * @param count how many of it
     * @return the moved wall clock, or the one the account shows for a moment past what a wall clock holds
     */
    static LocalDateTime plus(final LocalDateTime wallClock, final IntervalUnit unit, final long count) {
        final long seconds;
        final long nanos;
        switch (unit) {
            case MINUTE:
                seconds = count * SECONDS_PER_MINUTE;
                nanos = 0L;
                break;
            case SECOND:
                seconds = count;
                nanos = 0L;
                break;
            case MILLISECOND:
                seconds = count / MILLIS_PER_SECOND;
                nanos = count % MILLIS_PER_SECOND * NANOS_PER_MILLI;
                break;
            case MICROSECOND:
                seconds = count / MICROS_PER_SECOND;
                nanos = count % MICROS_PER_SECOND * NANOS_PER_MICRO;
                break;
            default:
                seconds = count / NANOS_PER_SECOND;
                nanos = count % NANOS_PER_SECOND;
                break;
        }
        long epochSecond = wallClock.toEpochSecond(ZoneOffset.UTC) + seconds;
        long nano = wallClock.getNano() + nanos;
        if (nano < 0L) {
            epochSecond--;
            nano += NANOS_PER_SECOND;
        } else if (nano >= NANOS_PER_SECOND) {
            epochSecond++;
            nano -= NANOS_PER_SECOND;
        }
        if (epochSecond >= LOWEST_EPOCH_SECOND && epochSecond <= HIGHEST_EPOCH_SECOND) {
            return LocalDateTime.ofEpochSecond(epochSecond, (int) nano, ZoneOffset.UTC);
        }
        return shownWallClock(epochSecond, (int) nano);
    }

    /** The wall clock the account's renderer spells for an epoch second past what a wall clock holds. */
    private static LocalDateTime shownWallClock(final long epochSecond, final int nano) {
        final long fromYearZero = epochSecond + SECONDS_FROM_YEAR_ZERO;
        final long secondOfDay = Math.floorMod(fromYearZero, SECONDS_PER_DAY);
        // Howard Hinnant's civil-from-days, counted from the March of the year 0.
        final long days = Math.floorDiv(fromYearZero, SECONDS_PER_DAY) - SECONDS_FROM_YEAR_ZERO / SECONDS_PER_DAY
            + DAYS_FROM_MARCH_OF_YEAR_ZERO;
        final long era = Math.floorDiv(days, DAYS_PER_ERA);
        final long dayOfEra = days - era * DAYS_PER_ERA;
        final long yearOfEra = (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365;
        final long dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100);
        final long marchMonth = (5 * dayOfYear + 2) / 153;
        final int day = (int) (dayOfYear - (153 * marchMonth + 2) / 5 + 1);
        final int month = (int) (marchMonth < 10 ? marchMonth + 3 : marchMonth - 9);
        final int year = (int) (yearOfEra + era * YEARS_PER_ERA + (month <= 2 ? 1 : 0));
        try {
            return LocalDateTime.of(year, month, day, (int) (secondOfDay / 3600), (int) (secondOfDay % 3600 / 60),
                (int) (secondOfDay % 60), nano);
        } catch (final DateTimeException beyondAWallClock) {
            throw new RuntimeException("Timestamp " + epochSecond + " is out of range");
        }
    }
}
