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

import dev.frostlake.types.IntervalQualifier;
import dev.frostlake.values.DayTimeInterval;
import dev.frostlake.values.YearMonthInterval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * An interval cell as it leaves the engine for a client. The engine carries an interval as one of two
 * objects — a {@link DayTimeInterval} (a TIMESTAMP difference and the arithmetic over one) or the
 * literal's own value (a unit-suffixed {@code INTERVAL '1' DAY} projected as written) — and neither may
 * cross to a client as an object. It crosses as the number the account's own JSON result rows carry for
 * it: a day-time interval is its signed count of NANOSECONDS and a year-month interval its signed count
 * of MONTHS, so {@code INTERVAL '1' DAY} is {@code 86400000000000}, a TIMESTAMP difference of a day and an
 * hour {@code 90000000000000}, and {@code INTERVAL '1-2' YEAR TO MONTH} {@code 14} (live-verified). What a
 * JDBC client makes of that number is the driver's business: see {@code jdbc.IntervalColumnReads}.
 */
public final class IntervalCells {

    /** The type code Snowflake's JDBC driver reports for a day-time interval column. */
    public static final int DAY_TIME_TYPE = 50006;

    /** The type code Snowflake's JDBC driver reports for a year-month interval column. */
    public static final int YEAR_MONTH_TYPE = 50005;

    private static final BigDecimal NANOS_PER_SECOND = BigDecimal.valueOf(1_000_000_000L);

    private IntervalCells() {
    }

    /**
     * Whether a cell is an interval of either family.
     *
     * @param cell the cell
     * @return true for an engine interval object
     */
    public static boolean isInterval(final Object cell) {
        return cell instanceof DayTimeInterval || cell instanceof YearMonthInterval || cell instanceof IntervalValue;
    }

    /**
     * The driver's type code for an interval cell: {@link #DAY_TIME_TYPE}, {@link #YEAR_MONTH_TYPE}, or 0 for
     * anything else — a multi-part literal that mixes the two families included.
     *
     * @param cell the cell
     * @return the type code, or 0
     */
    public static int typeCode(final Object cell) {
        if (cell instanceof DayTimeInterval) {
            return DAY_TIME_TYPE;
        }
        if (cell instanceof YearMonthInterval) {
            return YEAR_MONTH_TYPE;
        }
        if (!(cell instanceof IntervalValue)) {
            return 0;
        }
        boolean dayTime = false;
        boolean yearMonth = false;
        for (IntervalValue part = (IntervalValue) cell; part != null; part = part.getRest()) {
            if (monthsPerUnit(part.getUnit()) > 0) {
                yearMonth = true;
            } else {
                dayTime = true;
            }
        }
        return dayTime == yearMonth ? 0 : dayTime ? DAY_TIME_TYPE : YEAR_MONTH_TYPE;
    }

    /**
     * The fields an interval cell spans when nothing declares its column's type: a TIMESTAMP difference is
     * {@code DAY(9) TO SECOND(9)}, and a one-unit literal the qualifier of its unit, as the literal is typed.
     *
     * @param cell an interval cell
     * @return its qualifier, or null for a literal that mixes the two families
     */
    public static IntervalQualifier qualifierOf(final Object cell) {
        if (cell instanceof IntervalLiteral) {
            return ((IntervalLiteral) cell).qualifier();
        }
        if (cell instanceof DayTimeInterval) {
            return IntervalQualifier.DAY_TO_SECOND;
        }
        if (cell instanceof YearMonthInterval) {
            return IntervalQualifier.YEAR_TO_MONTH;
        }
        final int code = typeCode(cell);
        if (code == 0) {
            return null;
        }
        final IntervalValue literal = (IntervalValue) cell;
        if (literal.getRest() == null) {
            switch (literal.getUnit()) {
                case DAY:
                    return IntervalQualifier.DAY;
                case HOUR:
                    return IntervalQualifier.HOUR;
                case MINUTE:
                    return IntervalQualifier.MINUTE;
                case SECOND:
                    return IntervalQualifier.SECOND;
                case YEAR:
                    return IntervalQualifier.YEAR;
                case MONTH:
                    return IntervalQualifier.MONTH;
                default:
                    break;
            }
        }
        return code == YEAR_MONTH_TYPE ? IntervalQualifier.YEAR_TO_MONTH : IntervalQualifier.DAY_TO_SECOND;
    }

    /**
     * A day-time interval cell's signed nanoseconds.
     *
     * @param cell a cell whose {@link #typeCode} is {@link #DAY_TIME_TYPE}
     * @return its nanoseconds
     */
    public static BigInteger nanos(final Object cell) {
        if (cell instanceof DayTimeInterval) {
            return ((DayTimeInterval) cell).seconds().multiply(NANOS_PER_SECOND).setScale(0, RoundingMode.HALF_UP)
                .toBigIntegerExact();
        }
        BigDecimal total = BigDecimal.ZERO;
        for (IntervalValue part = (IntervalValue) cell; part != null; part = part.getRest()) {
            total = total.add(amount(part).multiply(BigDecimal.valueOf(nanosPerUnit(part.getUnit()))));
        }
        return total.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
    }

    /**
     * A year-month interval cell's signed months.
     *
     * @param cell a cell whose {@link #typeCode} is {@link #YEAR_MONTH_TYPE}
     * @return its months
     */
    public static BigInteger months(final Object cell) {
        if (cell instanceof YearMonthInterval) {
            return BigInteger.valueOf(((YearMonthInterval) cell).months());
        }
        BigDecimal total = BigDecimal.ZERO;
        for (IntervalValue part = (IntervalValue) cell; part != null; part = part.getRest()) {
            total = total.add(amount(part).multiply(BigDecimal.valueOf(monthsPerUnit(part.getUnit()))));
        }
        return total.setScale(0, RoundingMode.HALF_UP).toBigIntegerExact();
    }

    /**
     * The number an interval cell crosses to a client as: its nanoseconds or its months.
     *
     * @param cell an interval cell
     * @return the number
     */
    public static BigInteger number(final Object cell) {
        return typeCode(cell) == YEAR_MONTH_TYPE ? months(cell) : nanos(cell);
    }

    /**
     * The text an interval cell crosses the wire as: its nanoseconds or months in plain decimal digits,
     * and — for a literal mixing both families, which no projection answers — the literal's own text,
     * so the cell is never serialized as an object.
     *
     * @param cell an interval cell
     * @return the wire text
     */
    public static String wireText(final Object cell) {
        return typeCode(cell) == 0 ? cell.toString() : number(cell).toString();
    }

    /** The literal part's amount; the written text, so a fractional second stays exact. */
    private static BigDecimal amount(final IntervalValue part) {
        final Object value = part.getValue();
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    /** Months in one unit of a year-month interval, 0 for a day-time unit. */
    private static long monthsPerUnit(final IntervalUnit unit) {
        switch (unit) {
            case YEAR:
                return 12;
            case QUARTER:
                return 3;
            case MONTH:
                return 1;
            default:
                return 0;
        }
    }

    /** Nanoseconds in one unit of a day-time interval. */
    private static long nanosPerUnit(final IntervalUnit unit) {
        switch (unit) {
            case WEEK:
                return 7L * 86_400L * 1_000_000_000L;
            case DAY:
                return 86_400L * 1_000_000_000L;
            case HOUR:
                return 3_600L * 1_000_000_000L;
            case MINUTE:
                return 60L * 1_000_000_000L;
            case SECOND:
                return 1_000_000_000L;
            case MILLISECOND:
                return 1_000_000L;
            case MICROSECOND:
                return 1_000L;
            case NANOSECOND:
                return 1L;
            default:
                return 0L;
        }
    }
}
