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

import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalField;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.values.DayTimeInterval;

/**
 * The arithmetic an interval of either family takes part in, and the type each answers — every other pairing
 * with an interval is refused at the operator (all live-verified):
 *
 * <pre>
 *   i ± i (one family)               the span of both: DAY + HOUR is DAY TO HOUR, YEAR + MONTH YEAR TO MONTH;
 *                                    the leading precision one past the wider operand's, up to 9; the widest
 *                                    fractional precision of a SECOND on either side
 *   i * n, n * i, i / n              the interval's fields; the leading precision widened by the number's digits
 *                                    (its precision, or its scale plus one if more), up to 9
 *   temporal ± i, i + temporal       a DATE plus a year-month interval a DATE, plus a day-time one a
 *                                    TIMESTAMP_NTZ(9); a timestamp its own flavour at nine digits
 *   -i                               the interval's own type
 * </pre>
 *
 * <p>So {@code DAY(2) + DAY(3)} is a {@code DAY(4)}, {@code DAY(2) * 200} a {@code DAY(5)}, {@code DAY(2) * 0.5} a
 * {@code DAY(4)}, and {@code i + 1}, {@code i - d}, {@code i * i}, {@code i / i}, {@code 2 / i}, {@code i % 2}, a
 * FLOAT, VARIANT, text or BOOLEAN beside an interval, a TIME on either side and two families mixed are all
 * "Invalid argument types for function …".
 */
final class IntervalArithmeticTypes {

    private static final int MAX_PRECISION = 9;

    private IntervalArithmeticTypes() {
    }

    /**
     * Whether either operand is an interval.
     *
     * @param left  the left operand's type, or null
     * @param right the right operand's type, or null
     * @return whether this class judges the pair
     */
    static boolean involves(final DataType left, final DataType right) {
        return isInterval(left) || isInterval(right);
    }

    /**
     * An interval's type.
     *
     * @param type a type, or null
     * @return whether it is an interval of either family
     */
    static boolean isInterval(final DataType type) {
        return type instanceof IntervalDayTimeType || type instanceof IntervalYearMonthType;
    }

    /**
     * The type an arithmetic operator answers over a pair that holds an interval.
     *
     * @param operator the operator
     * @param left     the left operand's type
     * @param right    the right operand's type
     * @return the result type, or null when the account refuses the pair
     */
    static DataType resultOf(final BinaryOperator operator, final DataType left, final DataType right) {
        final boolean leftInterval = isInterval(left);
        final boolean rightInterval = isInterval(right);
        switch (operator) {
            case ADD:
                if (leftInterval && rightInterval) {
                    return span(left, right);
                }
                return leftInterval ? shifted(right, left) : shifted(left, right);
            case SUBTRACT:
                if (leftInterval) {
                    return rightInterval ? span(left, right) : null;
                }
                return shifted(left, right);
            case MULTIPLY:
                if (leftInterval && isExactNumber(right)) {
                    return scaled(left, (NumericType) right);
                }
                return rightInterval && isExactNumber(left) ? scaled(right, (NumericType) left) : null;
            case DIVIDE:
                return leftInterval && isExactNumber(right) ? scaled(left, (NumericType) right) : null;
            default:
                return null;
        }
    }

    /**
     * The type a number operand of no static type is read as from its value — a Snowflake Scripting variable
     * that a block's expression reads by name is such an operand: a FLOAT value a FLOAT, any other number an
     * exact NUMBER(38,0), which widens an interval's leading precision to nine.
     *
     * @param value the operand's value
     * @return its type, or null for a value that is no number
     */
    static DataType numberTypeOf(final Object value) {
        if (value instanceof Double || value instanceof Float) {
            return NumericType.FLOAT;
        }
        return value instanceof Number ? NumericType.NUMBER : null;
    }

    /** Two intervals of one family combined: the fields of both, one digit more than the wider leading field. */
    private static DataType span(final DataType left, final DataType right) {
        if (left instanceof IntervalYearMonthType && right instanceof IntervalYearMonthType) {
            final IntervalYearMonthType l = (IntervalYearMonthType) left;
            final IntervalYearMonthType r = (IntervalYearMonthType) right;
            return IntervalYearMonthType.of(spanned(l.getQualifier(), r.getQualifier()),
                widened(Math.max(l.getLeadingPrecision(), r.getLeadingPrecision()), 1));
        }
        if (!(left instanceof IntervalDayTimeType) || !(right instanceof IntervalDayTimeType)) {
            return null;
        }
        final IntervalDayTimeType l = (IntervalDayTimeType) left;
        final IntervalDayTimeType r = (IntervalDayTimeType) right;
        final IntervalQualifier qualifier = spanned(l.getQualifier(), r.getQualifier());
        return IntervalDayTimeType.of(qualifier, widened(Math.max(l.getLeadingPrecision(), r.getLeadingPrecision()), 1),
            Math.max(l.getFractionalPrecision(), r.getFractionalPrecision()));
    }

    /** The qualifier from the coarser leading field to the finer trailing one. */
    private static IntervalQualifier spanned(final IntervalQualifier left, final IntervalQualifier right) {
        final IntervalField leftLead = IntervalField.leadingOf(left);
        final IntervalField rightLead = IntervalField.leadingOf(right);
        final IntervalField leftTrail = IntervalField.trailingOf(left);
        final IntervalField rightTrail = IntervalField.trailingOf(right);
        return IntervalField.qualifier(leftLead.ordinal() <= rightLead.ordinal() ? leftLead : rightLead,
            leftTrail.ordinal() >= rightTrail.ordinal() ? leftTrail : rightTrail);
    }

    /** An interval scaled by an exact number: its own fields, the leading field widened by the number's digits. */
    private static DataType scaled(final DataType interval, final NumericType factor) {
        final int digits = Math.max(factor.getPrecision(), factor.getScale() + 1);
        if (interval instanceof IntervalYearMonthType) {
            final IntervalYearMonthType type = (IntervalYearMonthType) interval;
            return IntervalYearMonthType.of(type.getQualifier(), widened(type.getLeadingPrecision(), digits));
        }
        final IntervalDayTimeType type = (IntervalDayTimeType) interval;
        return IntervalDayTimeType.of(type.getQualifier(), widened(type.getLeadingPrecision(), digits),
            type.getFractionalPrecision());
    }

    private static int widened(final int precision, final int digits) {
        return (int) Math.min(MAX_PRECISION, (long) precision + digits);
    }

    /** What a DATE or a timestamp moved by an interval becomes, or null for any other subject. */
    private static DataType shifted(final DataType subject, final DataType interval) {
        if (!(subject instanceof DateTimeType)) {
            return null;
        }
        final String name = SqlTypeNames.canonical(subject);
        if ("DATE".equals(name)) {
            return interval instanceof IntervalYearMonthType ? subject : DateTimeType.TIMESTAMP_NTZ;
        }
        if (!name.startsWith("TIMESTAMP")) {
            return null;
        }
        final DateTimeType timestamp = (DateTimeType) subject;
        return timestamp.getPrecision() == DayTimeInterval.SCALE ? timestamp
            : new DateTimeType(timestamp.getName(), DayTimeInterval.SCALE, timestamp.hasTimeZone());
    }

    /** A NUMBER that is not a FLOAT — the only number an interval is scaled by. */
    private static boolean isExactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }
}
