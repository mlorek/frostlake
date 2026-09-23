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

import dev.frostlake.executor.expressions.IntervalUnit;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.DateTypeHelper;
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DateAdd extends BuiltInFunction {
    public DateAdd() { this("DATEADD"); }

    /**
     * The shift under one of its names — TIMEADD and TIMESTAMPADD are the same function, and a refusal
     * names the one the call was written with.
     *
     * @param name the name the function is registered under
     */
    public DateAdd(final String name) { super(name, DateTimeType.TIMESTAMP_NTZ); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(2) == null || args.get(1) == null) return null;
        if (SharedFunctionHelpers.isComponentOnlyUnit(args.get(0))) {
            throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), getName());
        }
        final String unit = SharedFunctionHelpers.canonicalDateUnit(args.get(0));
        final long amount = wholeAmount(args.get(1));
        // A TIME input computes on an epoch-day anchor and stays a TIME (wrapping within the
        // day) — its toString drops zero seconds, so it must never take the text-parse path.
        final boolean timeOnly = args.get(2) instanceof java.time.LocalTime;
        final LocalDateTime dt = timeOnly
            ? java.time.LocalDate.EPOCH.atTime((java.time.LocalTime) args.get(2))
            : SharedFunctionHelpers.toLocalDateTime(args.get(2));
        final IntervalUnit written = partOf(unit);
        if (written == null) {
            throw SharedFunctionHelpers.notADateTimeComponent(args.get(0), getName());
        }
        // A day or coarser, or an hour, counts in 32 bits, and weeks, quarters and years are multiplied
        // into days or months there too (see DateShiftAmount).
        final IntervalUnit part = DateShiftAmount.carriedUnit(written);
        final long count = DateShiftAmount.carriedCount(written,
            DateShiftAmount.isBounded(written) ? DateShiftAmount.within32Bits(amount) : amount);
        // A minute or finer takes any 64-bit count, in the account's own arithmetic (see FinePartShift).
        if (FinePartShift.isFine(part)) {
            return FinePartShift.shift(args.get(2), dt, part, count);
        }
        // A zoned timestamp moves as its flavour does: a TIMESTAMP_TZ at the offset it was written with,
        // a TIMESTAMP_LTZ as an instant read in the session's zone (see ZonedTimestampShift).
        if (ZonedTimestampShift.isZoned(args.get(2))) {
            return ZonedTimestampShift.shift(args.get(2), part, count);
        }
        final LocalDateTime result = ZonedTimestampShift.plus(dt, part, count);
        // A DATE-only input keeps DATE type for a day-or-larger unit; a sub-day unit or a timestamp input
        // yields a timestamp (Snowflake semantics).
        if (timeOnly) {
            return result.toLocalTime();
        }
        if (part.isWholeDay() && DateTypeHelper.isDateOnly(args.get(2))) {
            return result.toLocalDate();
        }
        return SharedFunctionHelpers.sameTimestampFlavour(args.get(2), result);
    }

    /** The part a unit word names, or null for a word DATEADD does not take. */
    private static IntervalUnit partOf(final String unit) {
        switch (unit) {
            case "YEAR": case "Y": case "YY": case "YYYY": return IntervalUnit.YEAR;
            case "QUARTER": case "Q": case "QTR": return IntervalUnit.QUARTER;
            case "MONTH": case "MM": case "MON": return IntervalUnit.MONTH;
            case "WEEK": case "WK": return IntervalUnit.WEEK;
            case "DAY": case "DD": case "D": return IntervalUnit.DAY;
            case "HOUR": case "H": case "HH": return IntervalUnit.HOUR;
            case "MINUTE": case "MIN": case "MI": return IntervalUnit.MINUTE;
            case "SECOND": case "SEC": case "S": return IntervalUnit.SECOND;
            case "MILLISECOND": case "MS": return IntervalUnit.MILLISECOND;
            case "MICROSECOND": case "US": return IntervalUnit.MICROSECOND;
            case "NANOSECOND": case "NS": return IntervalUnit.NANOSECOND;
            default: return null;
        }
    }

    /**
     * The amount as the whole number the shift is planned with. A fraction ROUNDS, half away from zero,
     * whether it is a NUMBER, a FLOAT or the text or VARIANT spelling one: {@code DATEADD(day, 1.5, d)},
     * {@code DATEADD(day, 1.5::FLOAT, d)} and {@code DATEADD(day, '1.5', d)} all move two days, and
     * {@code DATEADD(day, -0.5, d)} one day back. A text is trimmed first, and one that spells no number
     * is refused as the row reaches it (live-verified).
     */
    private static long wholeAmount(final Object amount) {
        final BigDecimal exact;
        if (amount instanceof Double || amount instanceof Float) {
            exact = BigDecimal.valueOf(((Number) amount).doubleValue());
        } else if (amount instanceof VariantValue && ((VariantValue) amount).node().isNumber()) {
            exact = ((VariantValue) amount).node().decimalValue();
        } else {
            final String spelled = amount instanceof VariantValue && ((VariantValue) amount).node().isTextual()
                ? ((VariantValue) amount).node().asText() : amount.toString();
            try {
                exact = new BigDecimal(spelled.trim());
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("Numeric value '" + spelled + "' is not recognized");
            }
        }
        return DateShiftAmount.rounded(exact);
    }

    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
