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

/**
 * An interval's text in the fields of a type, both ways: how a value prints as {@code INTERVAL DAY(9) TO
 * HOUR} or {@code INTERVAL SECOND(3,3)}, and how a string is read as one. Live prints every field after the
 * leading one with two digits and the leading one unpadded, a fraction with exactly the type's fractional
 * digits and none at all for a fraction of zero digits, and the sign always:
 *
 * <pre>
 *   DAY TO SECOND(9)  +1 02:03:04.500000000     HOUR TO MINUTE   +2:03      YEAR TO MONTH  -1-02
 *   DAY TO SECOND(0)  +1 02:03:04               HOUR TO SECOND   +2:03:04.500000000
 *   DAY TO MINUTE     +1 02:03                  MINUTE TO SECOND +3:04.500000000
 *   DAY TO HOUR       +1 02                     SECOND(2,3)      +1.000
 * </pre>
 *
 * <p>A string is read by the same fields: an optional sign, the leading field in at most its declared digits,
 * then the separators and fields the qualifier names, each later field in at most two digits, and a fraction
 * only where the type keeps one. A text that does not fit is refused naming every subtype's format, a field
 * past its digits in the precision sentence, and a later field out of its range in the range sentence
 * (live-verified).
 */
final class IntervalFields {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger NANOS_PER_MINUTE = NANOS_PER_SECOND.multiply(BigInteger.valueOf(60));
    private static final BigInteger NANOS_PER_HOUR = NANOS_PER_MINUTE.multiply(BigInteger.valueOf(60));
    private static final BigInteger NANOS_PER_DAY = NANOS_PER_HOUR.multiply(BigInteger.valueOf(24));
    private static final int MONTHS_PER_YEAR = 12;
    private static final int FRACTION_DIGITS = 9;
    private static final int LAST_HOUR = 23;
    private static final int LAST_MINUTE = 59;
    private static final int LAST_MONTH = 11;

    private IntervalFields() {
    }

    /**
     * An interval value's text in a type's fields. A value finer than the fields prints truncated toward
     * zero with its own sign, so minus three hours printed as a DAY is {@code -0}.
     *
     * @param cell      a day-time or year-month interval value
     * @param qualifier the fields to print
     * @param fraction  the fractional second digits to print, when a field is SECOND
     * @return the text
     */
    static String render(final Object cell, final IntervalQualifier qualifier, final int fraction) {
        if (cell instanceof YearMonthInterval) {
            final long months = ((YearMonthInterval) cell).months();
            final String sign = months < 0 ? "-" : "+";
            final long magnitude = Math.abs(months);
            switch (qualifier) {
                case YEAR_TO_MONTH:
                    return sign + magnitude / MONTHS_PER_YEAR + "-" + twoDigits(magnitude % MONTHS_PER_YEAR);
                case YEAR:
                    return sign + magnitude / MONTHS_PER_YEAR;
                default:
                    return sign + magnitude;
            }
        }
        if (!(cell instanceof DayTimeInterval)) {
            return String.valueOf(cell);
        }
        final BigInteger nanos = IntervalCells.nanos(cell);
        final String sign = nanos.signum() < 0 ? "-" : "+";
        final BigInteger magnitude = nanos.abs();
        final BigInteger[] seconds = magnitude.divideAndRemainder(NANOS_PER_SECOND);
        final String fractionText = fractionText(seconds[1], fraction);
        final long wholeSeconds = seconds[0].longValue();
        final long second = wholeSeconds % 60;
        final long minute = wholeSeconds / 60 % 60;
        final long hour = wholeSeconds / 3600 % 24;
        switch (qualifier) {
            case DAY_TO_SECOND:
                return sign + magnitude.divide(NANOS_PER_DAY) + " " + twoDigits(hour) + ":" + twoDigits(minute) + ":"
                    + twoDigits(second) + fractionText;
            case DAY_TO_MINUTE:
                return sign + magnitude.divide(NANOS_PER_DAY) + " " + twoDigits(hour) + ":" + twoDigits(minute);
            case DAY_TO_HOUR:
                return sign + magnitude.divide(NANOS_PER_DAY) + " " + twoDigits(hour);
            case DAY:
                return sign + magnitude.divide(NANOS_PER_DAY);
            case HOUR_TO_SECOND:
                return sign + magnitude.divide(NANOS_PER_HOUR) + ":" + twoDigits(minute) + ":" + twoDigits(second)
                    + fractionText;
            case HOUR_TO_MINUTE:
                return sign + magnitude.divide(NANOS_PER_HOUR) + ":" + twoDigits(minute);
            case HOUR:
                return sign + magnitude.divide(NANOS_PER_HOUR);
            case MINUTE_TO_SECOND:
                return sign + magnitude.divide(NANOS_PER_MINUTE) + ":" + twoDigits(second) + fractionText;
            case MINUTE:
                return sign + magnitude.divide(NANOS_PER_MINUTE);
            default:
                return sign + seconds[0] + fractionText;
        }
    }

    /**
     * A string read as an interval of a type's fields and precisions.
     *
     * @param text      the string
     * @param qualifier the type's fields
     * @param leading   the leading field's digits
     * @param fraction  the fractional second digits, when a field is SECOND
     * @return the day-time or year-month interval value
     */
    static Object parse(final String text, final IntervalQualifier qualifier, final int leading, final int fraction) {
        final IntervalTextScanner scan = new IntervalTextScanner(text.trim(), text, qualifier.isDayTime());
        final boolean negative = scan.sign();
        final String leadingDigits = scan.digits();
        if (leadingDigits.isEmpty()) {
            throw scan.invalidFormat();
        }
        if (leadingDigits.length() > leading) {
            throw scan.pastPrecision();
        }
        final BigInteger first = new BigInteger(leadingDigits);
        if (!qualifier.isDayTime()) {
            BigInteger months = qualifier == IntervalQualifier.MONTH ? first
                : first.multiply(BigInteger.valueOf(MONTHS_PER_YEAR));
            if (qualifier == IntervalQualifier.YEAR_TO_MONTH) {
                final int month = scan.laterField('-');
                scan.end();
                if (month > LAST_MONTH) {
                    throw new RuntimeException("Year-Month Interval '" + text
                        + "' is invalid, required that 0 <= MONTH <= 11");
                }
                months = months.add(BigInteger.valueOf(month));
            } else {
                scan.end();
            }
            return YearMonthInterval.ofMonths((negative ? months.negate() : months).longValueExact());
        }
        BigInteger nanos = first.multiply(leadingUnit(qualifier));
        int hour = 0;
        int minute = 0;
        int second = 0;
        switch (qualifier) {
            case DAY_TO_SECOND:
                hour = scan.laterField(' ');
                minute = scan.laterField(':');
                second = scan.laterField(':');
                break;
            case DAY_TO_MINUTE:
                hour = scan.laterField(' ');
                minute = scan.laterField(':');
                break;
            case DAY_TO_HOUR:
                hour = scan.laterField(' ');
                break;
            case HOUR_TO_SECOND:
                minute = scan.laterField(':');
                second = scan.laterField(':');
                break;
            case HOUR_TO_MINUTE:
                minute = scan.laterField(':');
                break;
            case MINUTE_TO_SECOND:
                second = scan.laterField(':');
                break;
            default:
                break;
        }
        BigInteger fractionNanos = BigInteger.ZERO;
        if (qualifier.endsInSecond()) {
            fractionNanos = scan.fraction(fraction);
        }
        scan.end();
        if (hour > LAST_HOUR || minute > LAST_MINUTE || second > LAST_MINUTE) {
            throw new RuntimeException("Day-Time Interval '" + text + "' is invalid, required that 0 <= HOUR <= 23, "
                + "0 <= MINUTE <= 59, 0 <= SECOND <= 59");
        }
        nanos = nanos.add(BigInteger.valueOf(hour).multiply(NANOS_PER_HOUR))
            .add(BigInteger.valueOf(minute).multiply(NANOS_PER_MINUTE))
            .add(BigInteger.valueOf(second).multiply(NANOS_PER_SECOND))
            .add(fractionNanos);
        return DayTimeInterval.ofSeconds(new BigDecimal(negative ? nanos.negate() : nanos).movePointLeft(FRACTION_DIGITS));
    }

    /** The nanoseconds in one unit of a day-time qualifier's leading field. */
    private static BigInteger leadingUnit(final IntervalQualifier qualifier) {
        return BigInteger.valueOf(qualifier.leadingUnitSize());
    }

    /** A fraction of a second in its first {@code digits} digits, and none at all for zero digits. */
    private static String fractionText(final BigInteger nanosOfSecond, final int digits) {
        if (digits <= 0) {
            return "";
        }
        final String nine = String.valueOf(1_000_000_000L + nanosOfSecond.longValue()).substring(1);
        return "." + nine.substring(0, Math.min(digits, FRACTION_DIGITS));
    }

    private static String twoDigits(final long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

}
