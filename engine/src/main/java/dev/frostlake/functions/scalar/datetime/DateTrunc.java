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
import dev.frostlake.types.DateTimeType;

import dev.frostlake.functions.scalar.DateTypeHelper;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

public class DateTrunc extends BuiltInFunction {
    public DateTrunc() { super("DATE_TRUNC", DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        return evaluate(args, "DATE_TRUNC");
    }

    /**
     * The truncation, reporting refusals under {@code reportedName}: TRUNC delegates here but names
     * ITSELF in its errors, and the account's sentences quote the function that was written.
     *
     * @param args the unit and the value
     * @param reportedName the function name to report refusals under
     * @return the truncated value
     */
    public Object evaluate(final List<Object> args, final String reportedName) {
        if (args.get(1) == null) return null;
        if (SharedFunctionHelpers.isComponentOnlyUnit(args.get(0))) {
            throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), reportedName);
        }
        final String unit = SharedFunctionHelpers.canonicalDateUnit(args.get(0));
        // A TIME carries no date, so it truncates on the clock alone and REFUSES a day-or-larger unit
        // rather than inventing one — live: "[DAY] is not a valid date/time component for function
        // DATE_TRUNC and type TIME."
        if (args.get(1) instanceof LocalTime) {
            return truncatedTime((LocalTime) args.get(1), unit, args.get(0), reportedName);
        }
        final LocalDateTime dt = SharedFunctionHelpers.toLocalDateTime(args.get(1));
        final LocalDateTime result;
        boolean dayOrLarger = true;
        switch (unit) {
            case "YEAR": case "YYYY": case "Y": result = dt.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS);
            break;
            case "QUARTER": case "Q": {
                final int m = ((dt.getMonthValue() - 1) / 3) * 3 + 1;
                result = dt.withMonth(m).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
                break;
            }
            case "MONTH": case "MM": result = dt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
            break;
            case "WEEK": case "WK": {
                result = dt.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).truncatedTo(ChronoUnit.DAYS);
                break;
            }
            case "DAY": case "DD": result = dt.truncatedTo(ChronoUnit.DAYS);
            break;
            case "HOUR": case "H":  result = dt.truncatedTo(ChronoUnit.HOURS);
            dayOrLarger = false;
            break;
            case "MINUTE": case "MIN": result = dt.truncatedTo(ChronoUnit.MINUTES);
            dayOrLarger = false;
            break;
            case "SECOND": case "SEC": result = dt.truncatedTo(ChronoUnit.SECONDS);
            dayOrLarger = false;
            break;
            case "MILLISECOND": result = dt.truncatedTo(ChronoUnit.MILLIS);
            dayOrLarger = false;
            break;
            case "MICROSECOND": result = dt.truncatedTo(ChronoUnit.MICROS);
            dayOrLarger = false;
            break;
            case "NANOSECOND": result = dt;
            dayOrLarger = false;
            break;
            default: throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), reportedName);
        }
        // A DATE-only input keeps DATE type for a day-or-larger unit; a sub-day truncation or a timestamp
        // input yields a timestamp (Snowflake semantics).
        if (dayOrLarger && DateTypeHelper.isDateOnly(args.get(1))) {
            return result.toLocalDate();
        }
        return SharedFunctionHelpers.sameTimestampFlavour(args.get(1), result);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }

    /** A TIME truncated on the clock; a day-or-larger unit has nothing to truncate and is refused. */
    private Object truncatedTime(final LocalTime time, final String unit, final Object rawUnit,
                                 final String reportedName) {
        switch (unit) {
            case "HOUR": return time.truncatedTo(ChronoUnit.HOURS);
            case "MINUTE": return time.truncatedTo(ChronoUnit.MINUTES);
            case "SECOND": return time.truncatedTo(ChronoUnit.SECONDS);
            case "MILLISECOND": return time.truncatedTo(ChronoUnit.MILLIS);
            case "MICROSECOND": return time.truncatedTo(ChronoUnit.MICROS);
            case "NANOSECOND": return time;
            default: throw SharedFunctionHelpers.notAComponentOfType(rawUnit, reportedName, "TIME");
        }
    }
}
