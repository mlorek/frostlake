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
import dev.frostlake.functions.scalar.WholeNumberArgument;
import dev.frostlake.functions.scalar.math.FloatingPointRefusal;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The whole number a date or time shift moves by (all live-verified):
 *
 * <ul>
 *   <li>the amount is rounded half away from zero, so 2147483647.5 counts as 2147483648;</li>
 *   <li>a count of days, weeks, months, quarters, years or hours must fit a 32-bit integer.
 *       {@code DATEADD(day, 2147483648, d)}, {@code ADD_MONTHS(d, 2147483648)} and {@code d + 2147483648}
 *       are all "Numeric value '2147483648' is out of range", and {@code d - 2147483649} is the same sentence
 *       for the amount it moves by, -2147483649;</li>
 *   <li>minutes and finer take any 64-bit count, and past that the amount is "Numeric value
 *       '9223372036854775808' is not recognized";</li>
 *   <li>a count of weeks, quarters or years is multiplied into days or months in 32 bits and wraps:
 *       {@code DATEADD(year, 2147483647, d)} moves back twelve months.</li>
 * </ul>
 */
public final class DateShiftAmount {

    private static final int DAYS_PER_WEEK = 7;
    private static final int MONTHS_PER_QUARTER = 3;
    private static final int MONTHS_PER_YEAR = 12;
    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);
    /** The first count a FIXED[SB4](9,0) does not hold. */
    private static final double NARROW_LIMIT = 1e9;
    /** The first count a FIXED[SB8](18,0) does not hold. */
    private static final double WIDE_LIMIT = 1e18;

    private DateShiftAmount() {
    }

    /**
     * An exact amount rounded to the whole number a shift reads.
     *
     * @param exact the amount
     * @return the rounded count
     */
    public static long rounded(final BigDecimal exact) {
        final BigDecimal whole = exact.setScale(0, RoundingMode.HALF_UP);
        if (whole.compareTo(LONG_MIN) < 0 || whole.compareTo(LONG_MAX) > 0) {
            throw new RuntimeException("Numeric value '" + whole.toPlainString() + "' is not recognized");
        }
        return whole.longValueExact();
    }

    /**
     * Refuses a FLOAT amount the fixed-point count its unit takes cannot hold (live-verified): a day or coarser, or
     * an hour, takes a FIXED[SB4](9,0) and anything finer a FIXED[SB8](18,0), so NaN, an infinity and an amount that
     * rounds half away from zero to a billion, or to 10^18, are refused in the cast's words, the amount printed as
     * C's {@code %g} prints it and the argument's nullability spelled in: {@code DATEADD(day, 3e9::FLOAT, d)} is
     * "Number out of representable range: type FIXED[SB4](9,0){not null}, value 3e+09", where the NUMBER 3000000000
     * is "Numeric value '3000000000' is out of range".
     *
     * @param amount   the amount
     * @param unit     the unit it counts
     * @param nullable whether the argument may be NULL
     */
    public static void requireFixedCount(final double amount, final IntervalUnit unit, final boolean nullable) {
        final boolean narrow = isBounded(unit);
        if (!Double.isNaN(amount) && Math.floor(Math.abs(amount) + 0.5) < (narrow ? NARROW_LIMIT : WIDE_LIMIT)) {
            return;
        }
        throw new RuntimeException("Number out of representable range: type "
            + (narrow ? WholeNumberArgument.PART : WholeNumberArgument.WIDE)
            + "{" + (nullable ? "nullable" : "not null") + "}, value " + FloatingPointRefusal.number(amount));
    }

    /**
     * A count that must fit a 32-bit integer.
     *
     * @param count the rounded count
     * @return the count
     */
    public static long within32Bits(final long count) {
        if (count < Integer.MIN_VALUE || count > Integer.MAX_VALUE) {
            throw new RuntimeException("Numeric value '" + count + "' is out of range");
        }
        return count;
    }

    /**
     * Whether a unit's count must fit a 32-bit integer — a day or coarser, or an hour.
     *
     * @param unit the unit
     * @return whether the count is bounded
     */
    public static boolean isBounded(final IntervalUnit unit) {
        return unit.isWholeDay() || unit == IntervalUnit.HOUR;
    }

    /**
     * The unit a week, quarter or year count is carried in: days for weeks, months for the other two.
     *
     * @param unit the written unit
     * @return the carrying unit, or the unit itself
     */
    public static IntervalUnit carriedUnit(final IntervalUnit unit) {
        if (unit == IntervalUnit.WEEK) {
            return IntervalUnit.DAY;
        }
        return unit == IntervalUnit.QUARTER || unit == IntervalUnit.YEAR ? IntervalUnit.MONTH : unit;
    }

    /**
     * A count in its carrying unit, multiplied in 32 bits as the account multiplies it.
     *
     * @param unit the written unit
     * @param count the count of that unit, within 32 bits
     * @return the count of the carrying unit
     */
    public static long carriedCount(final IntervalUnit unit, final long count) {
        switch (unit) {
            case WEEK:
                return (int) (count * DAYS_PER_WEEK);
            case QUARTER:
                return (int) (count * MONTHS_PER_QUARTER);
            case YEAR:
                return (int) (count * MONTHS_PER_YEAR);
            default:
                return count;
        }
    }
}
