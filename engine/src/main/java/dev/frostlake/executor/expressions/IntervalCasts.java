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
import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.YearMonthInterval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A value converted INTO an interval type — {@code CAST(x AS INTERVAL HOUR)}, {@code x::INTERVAL DAY(3) TO
 * SECOND(3)} and a write to a column declared that way share it (live-verified throughout):
 *
 * <ul>
 *   <li>a string is read in the type's fields ({@code '1 02'::INTERVAL DAY TO HOUR} is {@code +1 02});</li>
 *   <li>an interval of the same family is cut to the type's fields toward zero — a day and an hour as an
 *       {@code INTERVAL HOUR} is {@code +25}, {@code 0.9999} seconds as a {@code SECOND(3)} fraction
 *       {@code .999} — and refused past the leading digits: "Interval out of representable range, type:
 *       INTERVAL_DAY_TIME[SB8](2,6){not null} value: +100 00:00:00.000000000";</li>
 *   <li>an exact number is a count of the single field a one-field type spans, rounded half away from zero
 *       ({@code CAST(1.5 AS INTERVAL HOUR)} is {@code +2}), and a type of more than one field takes none:
 *       "Numeric value 1 cannot be cast to Interval type INTERVAL_DAY_TIME[SB16](9,5){not null}";</li>
 *   <li>every other family, the other interval family included, is refused while the statement compiles.</li>
 * </ul>
 *
 * <p>The converted value keeps its type's fields ({@link TypedDayTimeInterval}, {@link TypedYearMonthInterval}).
 */
public final class IntervalCasts {

    private static final int FRACTION_DIGITS = 9;
    private static final int FRACTION_CODE_STEP = 16;
    private static final long MONTHS_PER_YEAR = 12L;

    private IntervalCasts() {
    }

    /**
     * @param type a type, or null
     * @return whether it is an interval type of either family
     */
    public static boolean isIntervalType(final DataType type) {
        return type instanceof IntervalDayTimeType || type instanceof IntervalYearMonthType;
    }

    /**
     * Whether a source of this static type reaches an interval type without a compile-time refusal: a text,
     * an exact number, an interval of the same family, and a source of no known type.
     *
     * @param source the source's static type, or null
     * @param target an interval type
     * @return true when the conversion is left to the row
     */
    public static boolean reaches(final DataType source, final DataType target) {
        if (source == null || source instanceof StringType) {
            return true;
        }
        if (source instanceof NumericType) {
            return !NumericType.isApproximate(source);
        }
        return source instanceof IntervalDayTimeType ? target instanceof IntervalDayTimeType
            : source instanceof IntervalYearMonthType && target instanceof IntervalYearMonthType;
    }

    /**
     * The number an interval value casts to: its span in units of its own trailing field, the seconds of a
     * TIMESTAMP difference.
     *
     * @param interval a day-time or year-month interval value
     * @return the number, unrounded
     */
    public static BigDecimal numberOf(final Object interval) {
        return IntervalText.amount(interval, IntervalText.ownQualifier(interval));
    }

    /**
     * The type an interval value reads as when nothing declares it: its own fields, at the default digits.
     *
     * @param interval a day-time or year-month interval value
     * @return its type
     */
    public static DataType typeOfValue(final Object interval) {
        final IntervalQualifier own = IntervalText.ownQualifier(interval);
        return own.isDayTime() ? IntervalDayTimeType.of(own) : IntervalYearMonthType.of(own);
    }

    /**
     * The conversion function live names when it refuses a source: {@code TO_INTERVAL_DAY_TIME} or
     * {@code TO_INTERVAL_YEAR_MONTH}.
     *
     * @param target an interval type
     * @return the function's name
     */
    public static String conversionName(final DataType target) {
        return target instanceof IntervalYearMonthType ? "TO_INTERVAL_YEAR_MONTH" : "TO_INTERVAL_DAY_TIME";
    }

    /**
     * A value converted to an interval type.
     *
     * @param value          the value, or null
     * @param target         an interval type
     * @param nullableSource whether the source may be NULL, which the out-of-range sentence spells
     * @return the converted value, or null
     */
    public static Object convert(final Object value, final DataType target, final boolean nullableSource) {
        if (value == null) {
            return null;
        }
        final IntervalQualifier qualifier = qualifier(target);
        final int leading = leadingPrecision(target);
        final int fraction = fractionalPrecision(target);
        if (value instanceof String) {
            return typed(IntervalFields.parse((String) value, qualifier, leading, fraction), qualifier, fraction);
        }
        if (value instanceof DayTimeInterval && qualifier.isDayTime()) {
            final BigInteger cut = truncate(IntervalCells.nanos(value), step(qualifier, fraction));
            requireLeading(cut, qualifier, leading, target, nullableSource, value.toString());
            return typed(DayTimeInterval.ofSeconds(new BigDecimal(cut).movePointLeft(FRACTION_DIGITS)), qualifier,
                fraction);
        }
        if (value instanceof YearMonthInterval && !qualifier.isDayTime()) {
            final BigInteger cut = truncate(BigInteger.valueOf(((YearMonthInterval) value).months()),
                BigInteger.valueOf(qualifier.trailingUnitSize()));
            requireLeading(cut, qualifier, leading, target, nullableSource, value.toString());
            return typed(YearMonthInterval.ofMonths(cut.longValueExact()), qualifier, fraction);
        }
        if (value instanceof Number) {
            return fromNumber(numberOf((Number) value), qualifier, leading, fraction, target, nullableSource);
        }
        throw new RuntimeException("invalid type [" + value + "] for parameter '" + conversionName(target) + "'");
    }

    /**
     * An interval written through an {@code INSERT … VALUES} slot. Live hands the slot the number the value
     * carries — its nanoseconds or its months — as text, and reads that text back in the value's OWN fields
     * before the column converts it: {@code VALUES (INTERVAL '3' YEAR)} writes 36 years, {@code VALUES
     * (INTERVAL '14' MONTH)} 14 months, and {@code VALUES (INTERVAL '2' HOUR)} is refused as "Day-Time Interval
     * is '7200000000000' invalid, value of leading or fractional second field is greater than specified
     * precision/fsp" (live-verified). The same value written by a query converts as it is.
     *
     * @param value the value bound to the slot
     * @return the value the column converts
     */
    public static Object throughValuesSlot(final Object value) {
        if (!(value instanceof DayTimeInterval) && !(value instanceof YearMonthInterval)) {
            return value;
        }
        final IntervalQualifier own = IntervalText.ownQualifier(value);
        return IntervalFields.parse(IntervalCells.number(value).toString(), own, IntervalDayTimeType.DEFAULT_PRECISION,
            own.endsInSecond() ? FRACTION_DIGITS : 0);
    }

    /**
     * The type live spells inside its row-time interval sentences: {@code INTERVAL_DAY_TIME[SB16](153,3){not
     * null}} — the storage tag, the leading digits plus sixteen per fractional digit, the driver's scale code
     * for the fields, and whether the value may be NULL.
     *
     * @param target         an interval type
     * @param nullableSource whether the value may be NULL
     * @return the spelling
     */
    public static String internalSpelling(final DataType target, final boolean nullableSource) {
        final IntervalQualifier qualifier = qualifier(target);
        final String family = qualifier.isDayTime() ? "INTERVAL_DAY_TIME" : "INTERVAL_YEAR_MONTH";
        final int code = leadingPrecision(target) + FRACTION_CODE_STEP * fractionalPrecision(target);
        return family + "[" + qualifier.storageTag(leadingPrecision(target)) + "](" + code + ","
            + qualifier.driverScale() + "){" + (nullableSource ? "nullable" : "not null") + "}";
    }

    private static Object fromNumber(final BigDecimal number, final IntervalQualifier qualifier, final int leading,
                                     final int fraction, final DataType target, final boolean nullableSource) {
        if (qualifier.leadingUnitSize() != qualifier.trailingUnitSize()) {
            throw new RuntimeException("Numeric value " + number.toPlainString() + " cannot be cast to Interval type "
                + internalSpelling(target, nullableSource));
        }
        final int scale = qualifier == IntervalQualifier.SECOND ? fraction : 0;
        final BigDecimal units = number.setScale(scale, RoundingMode.HALF_UP);
        if (units.abs().setScale(0, RoundingMode.DOWN).compareTo(BigDecimal.TEN.pow(leading)) >= 0) {
            throw outOfRange(target, nullableSource, number.toPlainString());
        }
        final BigDecimal span = units.multiply(BigDecimal.valueOf(qualifier.leadingUnitSize()));
        if (!qualifier.isDayTime()) {
            return typed(YearMonthInterval.ofMonths(span.longValueExact()), qualifier, fraction);
        }
        return typed(DayTimeInterval.ofSeconds(span.movePointLeft(FRACTION_DIGITS)), qualifier, fraction);
    }

    /** A value cut to the type's fields: a multiple of {@code step}, toward zero. */
    private static BigInteger truncate(final BigInteger amount, final BigInteger step) {
        return amount.divide(step).multiply(step);
    }

    /** The finest unit a day-time type keeps, in nanoseconds: its trailing field, or its last fractional digit. */
    private static BigInteger step(final IntervalQualifier qualifier, final int fraction) {
        if (qualifier.endsInSecond()) {
            return BigInteger.TEN.pow(FRACTION_DIGITS - fraction);
        }
        return BigInteger.valueOf(qualifier.trailingUnitSize());
    }

    private static void requireLeading(final BigInteger amount, final IntervalQualifier qualifier, final int leading,
                                       final DataType target, final boolean nullableSource, final String shown) {
        final BigInteger limit = BigInteger.TEN.pow(leading).multiply(BigInteger.valueOf(qualifier.leadingUnitSize()));
        if (amount.abs().compareTo(limit) >= 0) {
            throw outOfRange(target, nullableSource, shown);
        }
    }

    private static RuntimeException outOfRange(final DataType target, final boolean nullableSource,
                                               final String shown) {
        return new RuntimeException("Interval out of representable range, type: "
            + internalSpelling(target, nullableSource) + " value: " + shown);
    }

    private static Object typed(final Object value, final IntervalQualifier qualifier, final int fraction) {
        if (value instanceof YearMonthInterval) {
            return new TypedYearMonthInterval(((YearMonthInterval) value).months(), qualifier);
        }
        return new TypedDayTimeInterval(((DayTimeInterval) value).seconds(), qualifier, fraction);
    }

    private static BigDecimal numberOf(final Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        }
        if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        }
        if (number instanceof Double || number instanceof Float) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.valueOf(number.longValue());
    }

    private static IntervalQualifier qualifier(final DataType target) {
        return target instanceof IntervalDayTimeType ? ((IntervalDayTimeType) target).getQualifier()
            : ((IntervalYearMonthType) target).getQualifier();
    }

    private static int leadingPrecision(final DataType target) {
        return target instanceof IntervalDayTimeType ? ((IntervalDayTimeType) target).getLeadingPrecision()
            : ((IntervalYearMonthType) target).getLeadingPrecision();
    }

    private static int fractionalPrecision(final DataType target) {
        return target instanceof IntervalDayTimeType ? ((IntervalDayTimeType) target).getFractionalPrecision() : 0;
    }
}
