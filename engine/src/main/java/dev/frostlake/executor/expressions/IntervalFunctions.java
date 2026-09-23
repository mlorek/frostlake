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

import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DataType;
import dev.frostlake.types.IntervalDayTimeType;
import dev.frostlake.types.IntervalYearMonthType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.YearMonthInterval;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The scalar functions that read an interval as one (live-verified throughout):
 *
 * <ul>
 *   <li>ABS keeps the interval and its type: {@code ABS(ts - ts2)} is an {@code INTERVAL DAY(9) TO SECOND(9)},
 *       {@code ABS(INTERVAL '-3' HOUR)} the {@code INTERVAL HOUR(9)} {@code +3}.</li>
 *   <li>EXTRACT, DATE_PART and the one-part functions read the interval's COMPONENTS, each signed like the
 *       interval and typed {@code NUMBER(9,0)}: over {@code -2 12:30:00} DAY is -2, HOUR -12 and MINUTE -30,
 *       over {@code +0 00:00:00.6} NANOSECOND is 600000000. A day-time interval has DAY (DAYOFMONTH alike),
 *       HOUR, MINUTE, SECOND and NANOSECOND, a year-month one YEAR and MONTH ({@code INTERVAL '14' MONTH} is
 *       year 1, month 2); any other part is refused while the statement compiles, naming the function —
 *       "invalid value [EPOCH_SECOND] for parameter 'EXTRACT date/time part'", "invalid value [YEAR] for
 *       parameter 'YEAR date/time part'".</li>
 * </ul>
 */
final class IntervalFunctions {

    /** What {@link #evaluate} answers for a call it does not compute. */
    static final Object NOT_APPLICABLE = new Object();

    /** The type every interval component reads as. */
    private static final NumericType COMPONENT = new NumericType("NUMBER", 9, 0);

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger SECONDS_PER_MINUTE = BigInteger.valueOf(60);
    private static final BigInteger MINUTES_PER_HOUR = BigInteger.valueOf(60);
    private static final BigInteger HOURS_PER_DAY = BigInteger.valueOf(24);
    private static final long MONTHS_PER_YEAR = 12L;

    /** The one-part functions that read no component of an interval, each refused in its own name. */
    private static final Set<String> OTHER_PART_FUNCTIONS = new HashSet<>(Arrays.asList(
        "WEEK", "WEEKOFYEAR", "WEEKISO", "DAYOFWEEK", "DAYOFWEEKISO", "DAYOFYEAR", "QUARTER", "YEAROFWEEK",
        "YEAROFWEEKISO"));

    private IntervalFunctions() {
    }

    /**
     * The value of a call over an interval argument this class computes, or {@link #NOT_APPLICABLE}.
     *
     * @param funcName the call's name, upper-cased
     * @param args     its evaluated arguments
     * @return the value, or {@link #NOT_APPLICABLE}
     */
    static Object evaluate(final String funcName, final List<Object> args) {
        if ("ABS".equals(funcName) && args.size() == 1) {
            return absolute(args.get(0));
        }
        if (("EXTRACT".equals(funcName) || "DATE_PART".equals(funcName)) && args.size() == 2
                && isInterval(args.get(1)) && args.get(0) != null) {
            return component(canonicalPart(args.get(0).toString()), args.get(1));
        }
        final String implied = impliedPart(funcName);
        if (implied != null && args.size() == 1 && isInterval(args.get(0))) {
            return component(implied, args.get(0));
        }
        return NOT_APPLICABLE;
    }

    /**
     * The static type of a call over a statically interval argument, or null when this class does not type it.
     *
     * @param funcName   the call's name, upper-cased
     * @param args       its arguments
     * @param inferencer the inferencer the arguments are typed with
     * @return the type, or null
     */
    static DataType resultType(final String funcName, final List<Expression> args, final TypeInferencer inferencer) {
        if ("ABS".equals(funcName) && args.size() == 1) {
            final DataType argument = inferencer.infer(args.get(0));
            return isIntervalType(argument) ? argument : null;
        }
        if (("EXTRACT".equals(funcName) || "DATE_PART".equals(funcName)) && args.size() == 2) {
            return isIntervalType(inferencer.infer(args.get(1))) ? COMPONENT : null;
        }
        if (impliedPart(funcName) != null && args.size() == 1) {
            return isIntervalType(inferencer.infer(args.get(0))) ? COMPONENT : null;
        }
        return null;
    }

    /**
     * The argument a call reads a component from — EXTRACT's and DATE_PART's second, a one-part function's
     * only one — or null for any other call.
     *
     * @param funcName the call's name, upper-cased
     * @param args     its arguments
     * @return the argument read, or null
     */
    static Expression componentSource(final String funcName, final List<Expression> args) {
        if (("EXTRACT".equals(funcName) || "DATE_PART".equals(funcName)) && args.size() == 2) {
            return args.get(1);
        }
        return impliedPart(funcName) != null && args.size() == 1 ? args.get(0) : null;
    }

    /**
     * The refusal of a part an interval does not have, or null.
     *
     * @param funcName  the call's name, upper-cased
     * @param partWord  the part as the refusal echoes it, for EXTRACT and DATE_PART; ignored otherwise
     * @param valueType the static type of the value the part is read from
     * @return the sentence, or null
     */
    static String partRefusal(final String funcName, final String partWord, final DataType valueType) {
        if (!isIntervalType(valueType)) {
            return null;
        }
        final boolean dayTime = valueType instanceof IntervalDayTimeType;
        if ("EXTRACT".equals(funcName) || "DATE_PART".equals(funcName)) {
            if (partWord == null || hasPart(dayTime, canonicalPart(partWord))) {
                return null;
            }
            return "SQL compilation error:\ninvalid value [" + partWord + "] for parameter '" + funcName
                + " date/time part'";
        }
        final String implied = impliedPart(funcName);
        final boolean partFunction = implied != null || OTHER_PART_FUNCTIONS.contains(funcName);
        if (!partFunction || implied != null && hasPart(dayTime, implied)) {
            return null;
        }
        return "SQL compilation error:\ninvalid value [" + funcName + "] for parameter '" + funcName
            + " date/time part'";
    }

    private static boolean hasPart(final boolean dayTime, final String part) {
        if (dayTime) {
            return "DAY".equals(part) || "HOUR".equals(part) || "MINUTE".equals(part) || "SECOND".equals(part)
                || "NANOSECOND".equals(part);
        }
        return "YEAR".equals(part) || "MONTH".equals(part);
    }

    /** The component a one-part function reads, or null for a function that names none of an interval's. */
    private static String impliedPart(final String funcName) {
        switch (funcName) {
            case "DAY":
            case "DAYOFMONTH":
                return "DAY";
            case "HOUR":
            case "MINUTE":
            case "SECOND":
            case "YEAR":
            case "MONTH":
                return funcName;
            default:
                return null;
        }
    }

    /** A part word reduced to the component it names, its aliases and plurals included. */
    private static String canonicalPart(final String word) {
        final String unit = SharedFunctionHelpers.canonicalDateUnit(word).toUpperCase(Locale.ROOT);
        switch (unit) {
            case "D": case "DD": case "DAYOFMONTH": case "DAYS":
                return "DAY";
            case "H": case "HH": case "HR": case "HOURS":
                return "HOUR";
            case "MI": case "MIN": case "MINUTES":
                return "MINUTE";
            case "S": case "SEC": case "SECONDS":
                return "SECOND";
            case "NS": case "NSECOND": case "NANOSECONDS":
                return "NANOSECOND";
            case "Y": case "YY": case "YYY": case "YYYY": case "YR": case "YEARS":
                return "YEAR";
            case "MM": case "MON": case "MONS": case "MONTHS":
                return "MONTH";
            default:
                return unit;
        }
    }

    private static Object component(final String part, final Object interval) {
        if (interval instanceof YearMonthInterval) {
            final long months = ((YearMonthInterval) interval).months();
            final long magnitude = Math.abs(months);
            final long value = "YEAR".equals(part) ? magnitude / MONTHS_PER_YEAR : magnitude % MONTHS_PER_YEAR;
            return Long.valueOf(months < 0 ? -value : value);
        }
        final BigInteger nanos = IntervalCells.nanos(interval);
        final BigInteger[] seconds = nanos.abs().divideAndRemainder(NANOS_PER_SECOND);
        final BigInteger value;
        switch (part) {
            case "DAY":
                value = seconds[0].divide(SECONDS_PER_MINUTE).divide(MINUTES_PER_HOUR).divide(HOURS_PER_DAY);
                break;
            case "HOUR":
                value = seconds[0].divide(SECONDS_PER_MINUTE).divide(MINUTES_PER_HOUR).mod(HOURS_PER_DAY);
                break;
            case "MINUTE":
                value = seconds[0].divide(SECONDS_PER_MINUTE).mod(MINUTES_PER_HOUR);
                break;
            case "SECOND":
                value = seconds[0].mod(SECONDS_PER_MINUTE);
                break;
            default:
                value = seconds[1];
                break;
        }
        return Long.valueOf(nanos.signum() < 0 ? -value.longValue() : value.longValue());
    }

    private static Object absolute(final Object value) {
        if (value instanceof DayTimeInterval) {
            return ((DayTimeInterval) value).seconds().signum() < 0 ? ((DayTimeInterval) value).negated() : value;
        }
        if (value instanceof YearMonthInterval) {
            final long months = ((YearMonthInterval) value).months();
            return months < 0 ? YearMonthInterval.ofMonths(-months) : value;
        }
        return NOT_APPLICABLE;
    }

    private static boolean isInterval(final Object value) {
        return value instanceof DayTimeInterval || value instanceof YearMonthInterval;
    }

    private static boolean isIntervalType(final DataType type) {
        return type instanceof IntervalDayTimeType || type instanceof IntervalYearMonthType;
    }
}
