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
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalField;
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.YearMonthInterval;

import java.math.BigInteger;

/**
 * An interval computed by arithmetic, as the type its expression declares holds it (all live-verified):
 *
 * <ul>
 *   <li>a product or a quotient keeps no part finer than the type's trailing field — {@code INTERVAL '1' DAY /
 *       2} is zero days, {@code INTERVAL '1' DAY * 1.5} one day, {@code INTERVAL '1' YEAR * 1.5} one year,
 *       {@code INTERVAL '1 01' DAY TO HOUR * 1.5} a day and 13 hours — the value truncated toward zero after
 *       the product was rounded half away from zero to the nanosecond or the month;</li>
 *   <li>a result whose leading field outgrows the type's leading precision is refused, naming the operation
 *       and the type the way the account's storage layer spells it: "Interval out of representable range
 *       after multiply, type: INTERVAL_DAY_TIME[SB16](9,6){not null}" — the storage tag, the fractional
 *       precision times sixteen plus the leading precision, the code the fields are reported by, and whether
 *       the expression may be null.</li>
 * </ul>
 *
 * <p>Either way the value carries its type's fields, so it prints and casts as they say.
 */
final class IntervalResults {

    private static final int FRACTION_DIGITS = 9;
    private static final int FRACTION_WEIGHT = 16;

    private IntervalResults() {
    }

    /**
     * An interval result as its expression's type holds it.
     *
     * @param value     the computed value
     * @param type      the type the expression declares
     * @param operation the operation's name in the range refusal — plus, minus, multiply or divide — or null
     *                  when the result cannot outgrow its type
     * @param truncate  whether parts finer than the trailing field are dropped
     * @param nullable  whether the expression may be null
     * @return the settled value; any value that is no interval of the declared family, unchanged
     */
    static Object settle(final Object value, final DataType type, final String operation, final boolean truncate,
                         final boolean nullable) {
        if (type instanceof IntervalDayTimeType && value instanceof DayTimeInterval) {
            final IntervalDayTimeType dayTime = (IntervalDayTimeType) type;
            final IntervalQualifier qualifier = dayTime.getQualifier();
            BigInteger nanos = IntervalCells.nanos(value);
            if (truncate) {
                final BigInteger unit = IntervalField.trailingOf(qualifier) == IntervalField.SECOND
                    ? BigInteger.TEN.pow(FRACTION_DIGITS - dayTime.getFractionalPrecision())
                    : BigInteger.valueOf(IntervalField.trailingOf(qualifier).storageUnits());
                nanos = nanos.divide(unit).multiply(unit);
            }
            requireRepresentable(nanos, qualifier, dayTime.getLeadingPrecision(), operation, "INTERVAL_DAY_TIME["
                + dayTime.storageTag() + "](" + (dayTime.getFractionalPrecision() * FRACTION_WEIGHT
                + dayTime.getLeadingPrecision()) + "," + qualifier.driverScale() + ")", nullable);
            return new DayTimeIntervalLiteral(nanos, qualifier, dayTime.getFractionalPrecision());
        }
        if (type instanceof IntervalYearMonthType && value instanceof YearMonthInterval) {
            final IntervalYearMonthType yearMonth = (IntervalYearMonthType) type;
            final IntervalQualifier qualifier = yearMonth.getQualifier();
            long months = ((YearMonthInterval) value).months();
            if (truncate) {
                final long unit = IntervalField.trailingOf(qualifier).storageUnits();
                months = months / unit * unit;
            }
            requireRepresentable(BigInteger.valueOf(months), qualifier, yearMonth.getLeadingPrecision(), operation,
                "INTERVAL_YEAR_MONTH[" + yearMonth.storageTag() + "](" + yearMonth.getLeadingPrecision() + ","
                    + qualifier.driverScale() + ")", nullable);
            return new YearMonthIntervalLiteral(months, qualifier);
        }
        return value;
    }

    /**
     * The type an interval result is settled to: the one its expression declares, or, where no static type is
     * known — an operand a block's expression reads by name — the fields the operand's own value carries.
     *
     * @param declared the type the expression declares, or null
     * @param operand  the operand's value
     * @return the type, or null when neither says
     */
    static DataType typeOr(final DataType declared, final Object operand) {
        if (declared != null) {
            return declared;
        }
        if (operand instanceof DayTimeIntervalLiteral) {
            final DayTimeIntervalLiteral literal = (DayTimeIntervalLiteral) operand;
            return IntervalDayTimeType.of(literal.qualifier(), IntervalDayTimeType.DEFAULT_PRECISION,
                literal.fractionalPrecision());
        }
        if (operand instanceof YearMonthIntervalLiteral) {
            return IntervalYearMonthType.of(((YearMonthIntervalLiteral) operand).qualifier());
        }
        return null;
    }

    /** Refuses a value whose leading field holds more digits than the leading precision allows. */
    private static void requireRepresentable(final BigInteger value, final IntervalQualifier qualifier,
                                             final int leadingPrecision, final String operation,
                                             final String storage, final boolean nullable) {
        if (operation == null) {
            return;
        }
        final BigInteger limit = BigInteger.TEN.pow(leadingPrecision)
            .multiply(BigInteger.valueOf(IntervalField.leadingOf(qualifier).storageUnits()));
        if (value.abs().compareTo(limit) >= 0) {
            throw new RuntimeException("Interval out of representable range after " + operation + ", type: "
                + storage + (nullable ? "{nullable}" : "{not null}"));
        }
    }
}
