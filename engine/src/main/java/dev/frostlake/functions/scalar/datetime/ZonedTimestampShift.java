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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Shifting a timestamp by one date or time part, as {@code DATEADD} and {@code + INTERVAL} do, for the wall clock
 * of a TIMESTAMP_NTZ and for the two zoned flavours. A TIMESTAMP_TZ keeps the offset it was written with, so its
 * wall clock moves like any other. A TIMESTAMP_LTZ is an instant read in the session's zone, and across a
 * daylight-saving change the three families of part move it differently:
 * <ul>
 *   <li>a part shorter than a day moves the INSTANT: {@code 2024-03-09 12:00 -0800} plus 24 hours is
 *       {@code 2024-03-10 13:00 -0700};</li>
 *   <li>a DAY or a WEEK moves the instant by whole days and then by the change in the zone's offset:
 *       plus one day is {@code 2024-03-10 12:00 -0700}, and {@code 2024-03-09 02:30 -0800} plus one day,
 *       whose wall clock the change skips, is {@code 2024-03-10 01:30 -0800};</li>
 *   <li>a MONTH, a QUARTER or a YEAR moves the wall clock, and a skipped wall clock lands after the change
 *       ({@code 2024-02-10 02:30} plus one month is {@code 2024-03-10 03:30 -0700}) while a repeated one
 *       takes the earlier offset.</li>
 * </ul>
 * Live-verified in America/Los_Angeles and Europe/London.
 */
public final class ZonedTimestampShift {

    private static final long SECONDS_PER_DAY = 86_400L;

    private ZonedTimestampShift() {
    }

    /**
     * A wall clock moved by {@code amount} of {@code unit}.
     *
     * @param wallClock the wall clock
     * @param unit      the part
     * @param amount    how many of it, negative to move back
     * @return the moved wall clock
     */
    public static LocalDateTime plus(final LocalDateTime wallClock, final IntervalUnit unit, final long amount) {
        switch (unit) {
            case YEAR:
                return wallClock.plusYears(amount);
            case QUARTER:
                return wallClock.plusMonths(amount * 3);
            case MONTH:
                return wallClock.plusMonths(amount);
            case WEEK:
                return wallClock.plusWeeks(amount);
            case DAY:
                return wallClock.plusDays(amount);
            case HOUR:
                return wallClock.plusHours(amount);
            case MINUTE:
                return wallClock.plusMinutes(amount);
            case SECOND:
                return wallClock.plusSeconds(amount);
            case MILLISECOND:
                return wallClock.plusNanos(amount * 1_000_000L);
            case MICROSECOND:
                return wallClock.plusNanos(amount * 1_000L);
            case NANOSECOND:
                return wallClock.plusNanos(amount);
            default:
                throw new RuntimeException("Unsupported interval unit: " + unit);
        }
    }

    /**
     * Whether a value is one of the zoned carriers this shifts: a TIMESTAMP_LTZ ({@link OffsetDateTime}) or a
     * TIMESTAMP_TZ ({@link ZonedDateTime}).
     *
     * @param value the value
     * @return whether it is zoned
     */
    public static boolean isZoned(final Object value) {
        return value instanceof OffsetDateTime || value instanceof ZonedDateTime;
    }

    /**
     * A zoned timestamp moved by {@code amount} of {@code unit}, in its own flavour.
     *
     * @param zoned  a TIMESTAMP_LTZ or TIMESTAMP_TZ value
     * @param unit   the part
     * @param amount how many of it, negative to move back
     * @return the moved value, of the same flavour
     */
    public static Object shift(final Object zoned, final IntervalUnit unit, final long amount) {
        if (zoned instanceof ZonedDateTime) {
            final ZonedDateTime written = (ZonedDateTime) zoned;
            return plus(written.toLocalDateTime(), unit, amount).atZone(written.getOffset());
        }
        final ZoneId zone = SessionZone.current();
        final Instant instant = ((OffsetDateTime) zoned).toInstant();
        if (unit == IntervalUnit.DAY || unit == IntervalUnit.WEEK) {
            final long days = unit == IntervalUnit.WEEK ? amount * 7 : amount;
            final Instant moved = instant.plusSeconds(days * SECONDS_PER_DAY);
            final long offsetChange = zone.getRules().getOffset(instant).getTotalSeconds()
                - zone.getRules().getOffset(moved).getTotalSeconds();
            return moved.plusSeconds(offsetChange).atZone(zone).toOffsetDateTime();
        }
        if (unit.isWholeDay()) {
            final LocalDateTime wallClock = plus(instant.atZone(zone).toLocalDateTime(), unit, amount);
            return ZonedDateTime.ofLocal(wallClock, zone, null).toOffsetDateTime();
        }
        final LocalDateTime utc = plus(LocalDateTime.ofInstant(instant, ZoneOffset.UTC), unit, amount);
        return utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toOffsetDateTime();
    }
}
