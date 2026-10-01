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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.IntegerResultWidths;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class DateDiff extends BuiltInFunction {
    public DateDiff() { this("DATEDIFF"); }

    /**
     * The difference under one of its names — TIMEDIFF and TIMESTAMPDIFF are the same function, and a
     * refusal names the one the call was written with.
     *
     * @param name the name the function is registered under
     */
    public DateDiff(final String name) { super(name, IntegerResultWidths.POSITION); }

    /** A TIME value anchors on the epoch day; toString would drop zero seconds. */
    private static LocalDateTime anchored(final Object v) {
        return v instanceof java.time.LocalTime
            ? java.time.LocalDate.EPOCH.atTime((java.time.LocalTime) v)
            : SharedFunctionHelpers.toLocalDateTime(v);
    }

    /**
     * The same wall clock moved to UTC when the value carries an offset — how the SUB-DAY units reach
     * the instant.
     *
     * <p>★ THE TWO HALVES OF DATEDIFF COUNT DIFFERENT THINGS. Day-and-larger units count WALL-CLOCK
     * boundaries, so two values a calendar month apart are one month apart whatever their offsets;
     * hour-and-smaller count the INSTANT. Across a daylight-saving change the two disagree by an hour,
     * and that is the cell that proves it: January to June in America/Los_Angeles is 3648 hours by the
     * clock and 3647 by the world, and live answers 3647.
     *
     * @param source the value as it arrived, which is where any offset is
     * @param wall the wall clock already read out of it
     * @return the wall clock in UTC, or unchanged when the value carries no offset
     */
    private static LocalDateTime atUtc(final Object source, final LocalDateTime wall) {
        if (source instanceof ZonedDateTime) {
            return wall.minusSeconds(((ZonedDateTime) source).getOffset().getTotalSeconds());
        }
        if (source instanceof OffsetDateTime) {
            return wall.minusSeconds(((OffsetDateTime) source).getOffset().getTotalSeconds());
        }
        return wall;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(1) == null || args.get(2) == null) return null;
        if (SharedFunctionHelpers.isComponentOnlyUnit(args.get(0))) {
            throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), getName());
        }
        final String unit = SharedFunctionHelpers.canonicalDateUnit(args.get(0));
        final LocalDateTime start = anchored(args.get(1));
        final LocalDateTime end   = anchored(args.get(2));
        // The sub-day units below use these instead — see atUtc.
        final LocalDateTime startAt = atUtc(args.get(1), start);
        final LocalDateTime endAt   = atUtc(args.get(2), end);
        // Snowflake DATEDIFF counts unit BOUNDARIES crossed, not elapsed whole units: both operands are
        // truncated to the unit first, so DATEDIFF(DAY, '23:00', '01:00 next day') = 1 and
        // DATEDIFF(HOUR, 10:59, 11:01) = 1. Weeks start on Monday (default WEEK_START).
        switch (unit) {
            case "YEAR": case "Y": case "YYYY": case "YR":
                return (long) (end.getYear() - start.getYear());
            case "QUARTER": case "Q": case "QTR":
                return (long) ((end.getYear() * 4 + (end.getMonthValue() - 1) / 3)
                    - (start.getYear() * 4 + (start.getMonthValue() - 1) / 3));
            case "MONTH": case "MM": case "MON":
                return (long) ((end.getYear() * 12 + end.getMonthValue()) - (start.getYear() * 12 + start.getMonthValue()));
            case "WEEK": case "WK":
                return Math.floorDiv(end.toLocalDate().toEpochDay() + 3, 7)
                    - Math.floorDiv(start.toLocalDate().toEpochDay() + 3, 7);
            case "DAY": case "DD": case "D":
                return end.toLocalDate().toEpochDay() - start.toLocalDate().toEpochDay();
            case "HOUR": case "H": case "HH":
                return ChronoUnit.HOURS.between(startAt.truncatedTo(ChronoUnit.HOURS), endAt.truncatedTo(ChronoUnit.HOURS));
            case "MINUTE": case "MIN": case "MI":
                return ChronoUnit.MINUTES.between(startAt.truncatedTo(ChronoUnit.MINUTES), endAt.truncatedTo(ChronoUnit.MINUTES));
            case "SECOND": case "SEC": case "S":
                return ChronoUnit.SECONDS.between(startAt.truncatedTo(ChronoUnit.SECONDS), endAt.truncatedTo(ChronoUnit.SECONDS));
            case "MILLISECOND": case "MS":
                return ChronoUnit.MILLIS.between(startAt.truncatedTo(ChronoUnit.MILLIS), endAt.truncatedTo(ChronoUnit.MILLIS));
            case "MICROSECOND": case "US":
                return ChronoUnit.MICROS.between(startAt.truncatedTo(ChronoUnit.MICROS), endAt.truncatedTo(ChronoUnit.MICROS));
            case "NANOSECOND":
                return ChronoUnit.NANOS.between(startAt, endAt);
            default: throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), getName());
        }
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
