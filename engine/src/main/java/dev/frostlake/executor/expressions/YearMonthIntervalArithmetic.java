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

import dev.frostlake.functions.scalar.datetime.ZonedTimestampShift;
import dev.frostlake.values.YearMonthInterval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The run-time arithmetic of a year-month interval, all live-verified:
 *
 * <ul>
 *   <li>an interval plus or minus an interval is the sum of their months;</li>
 *   <li>a DATE or a timestamp plus or minus one, and one plus a DATE or a timestamp, moves the wall clock by
 *       whole calendar months: {@code DATE '2024-01-31' + INTERVAL '1' MONTH} is {@code 2024-02-29}, still a DATE,
 *       and a TIMESTAMP_LTZ whose new wall clock a daylight-saving change skips lands after it;</li>
 *   <li>times an exact number is the product rounded half away from zero to a month ({@code '1' MONTH * 1.5} is
 *       two months), divided by one the quotient truncated toward zero ({@code '3' MONTH / 2} is one), and
 *       division by zero is "Interval division by zero".</li>
 * </ul>
 *
 * <p>Every other pairing is refused while the statement compiles (see {@link IntervalArithmeticTypes}), so each
 * method answers null for a pair it does not own and leaves the caller's own rules in charge.
 */
final class YearMonthIntervalArithmetic {

    private YearMonthIntervalArithmetic() {
    }

    /**
     * {@code left + right} when a year-month interval takes part, or null.
     *
     * @param left  the left operand's value
     * @param right the right operand's value
     * @return the sum, or null
     */
    static Object add(final Object left, final Object right) {
        if (left instanceof YearMonthInterval && right instanceof YearMonthInterval) {
            return YearMonthInterval.ofMonths(Math.addExact(months(left), months(right)));
        }
        if (right instanceof YearMonthInterval) {
            return shifted(left, months(right));
        }
        return left instanceof YearMonthInterval ? shifted(right, months(left)) : null;
    }

    /**
     * {@code left - right} when a year-month interval takes part, or null.
     *
     * @param left  the left operand's value
     * @param right the right operand's value
     * @return the difference, or null
     */
    static Object subtract(final Object left, final Object right) {
        if (left instanceof YearMonthInterval && right instanceof YearMonthInterval) {
            return YearMonthInterval.ofMonths(Math.subtractExact(months(left), months(right)));
        }
        return right instanceof YearMonthInterval ? shifted(left, Math.negateExact(months(right))) : null;
    }

    /**
     * {@code left * right} when one side is a year-month interval and the other a number, or null.
     *
     * @param left  the left operand's value
     * @param right the right operand's value
     * @return the scaled interval, or null
     */
    static Object multiply(final Object left, final Object right) {
        if (left instanceof YearMonthInterval && right instanceof Number) {
            return scaled(months(left), (Number) right);
        }
        if (right instanceof YearMonthInterval && left instanceof Number) {
            return scaled(months(right), (Number) left);
        }
        return null;
    }

    /**
     * {@code left / right} for a year-month interval over a number, or null.
     *
     * @param left  the dividend's value
     * @param right the divisor's value
     * @return the quotient, or null
     */
    static Object divide(final Object left, final Object right) {
        if (!(left instanceof YearMonthInterval) || !(right instanceof Number)) {
            return null;
        }
        final BigDecimal divisor = exact((Number) right);
        if (divisor.signum() == 0) {
            throw new RuntimeException("Interval division by zero");
        }
        return YearMonthInterval.ofMonths(BigDecimal.valueOf(months(left)).divide(divisor, 0, RoundingMode.DOWN)
            .longValueExact());
    }

    /**
     * {@code -value} for a year-month interval, or null.
     *
     * @param value the operand's value
     * @return the negated interval, or null
     */
    static Object negate(final Object value) {
        return value instanceof YearMonthInterval ? YearMonthInterval.ofMonths(Math.negateExact(months(value))) : null;
    }

    private static long months(final Object interval) {
        return ((YearMonthInterval) interval).months();
    }

    private static YearMonthInterval scaled(final long months, final Number factor) {
        return YearMonthInterval.ofMonths(BigDecimal.valueOf(months).multiply(exact(factor))
            .setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    /** A DATE or a timestamp moved by whole months, or null when it is neither. */
    private static Object shifted(final Object temporal, final long months) {
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).plusMonths(months);
        }
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).plusMonths(months);
        }
        return ZonedTimestampShift.isZoned(temporal) ? ZonedTimestampShift.shift(temporal, IntervalUnit.MONTH, months)
            : null;
    }

    private static BigDecimal exact(final Number number) {
        return number instanceof Double || number instanceof Float
            ? BigDecimal.valueOf(number.doubleValue()) : new BigDecimal(number.toString());
    }
}
