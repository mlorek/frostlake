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
import dev.frostlake.functions.scalar.DateTypeHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;

import java.time.LocalDateTime;
import java.util.List;

public class DateAdd extends BuiltInFunction {
    public DateAdd() { super("DATEADD", DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(2) == null) return null;
        if (SharedFunctionHelpers.isComponentOnlyUnit(args.get(0))) {
            throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), "DATEADD");
        }
        final String unit = SharedFunctionHelpers.canonicalDateUnit(args.get(0));
        final long amount = ((Number) args.get(1)).longValue();
        // A TIME input computes on an epoch-day anchor and stays a TIME (wrapping within the
        // day) — its toString drops zero seconds, so it must never take the text-parse path.
        final boolean timeOnly = args.get(2) instanceof java.time.LocalTime;
        final LocalDateTime dt = timeOnly
            ? java.time.LocalDate.EPOCH.atTime((java.time.LocalTime) args.get(2))
            : SharedFunctionHelpers.toLocalDateTime(args.get(2));
        final LocalDateTime result;
        boolean dayOrLarger = true;
        switch (unit) {
            case "YEAR": case "Y": case "YY": case "YYYY": result = dt.plusYears(amount);
            break;
            case "QUARTER": case "Q": case "QTR": result = dt.plusMonths(amount * 3);
            break;
            case "MONTH": case "MM": case "MON": result = dt.plusMonths(amount);
            break;
            case "WEEK": case "WK": result = dt.plusWeeks(amount);
            break;
            case "DAY": case "DD": case "D": result = dt.plusDays(amount);
            break;
            case "HOUR": case "H": case "HH": result = dt.plusHours(amount);
            dayOrLarger = false;
            break;
            case "MINUTE": case "MIN": case "MI": result = dt.plusMinutes(amount);
            dayOrLarger = false;
            break;
            case "SECOND": case "SEC": case "S": result = dt.plusSeconds(amount);
            dayOrLarger = false;
            break;
            case "MILLISECOND": case "MS": result = dt.plusNanos(amount * 1_000_000L);
            dayOrLarger = false;
            break;
            case "MICROSECOND": case "US": result = dt.plusNanos(amount * 1_000L);
            dayOrLarger = false;
            break;
            case "NANOSECOND": case "NS": result = dt.plusNanos(amount);
            dayOrLarger = false;
            break;
            default: throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), "DATEADD");
        }
        // A DATE-only input keeps DATE type for a day-or-larger unit; a sub-day unit or a timestamp input
        // yields a timestamp (Snowflake semantics).
        if (timeOnly) {
            return result.toLocalTime();
        }
        if (dayOrLarger && DateTypeHelper.isDateOnly(args.get(2))) {
            return result.toLocalDate();
        }
        return SharedFunctionHelpers.sameTimestampFlavour(args.get(2), result);
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
