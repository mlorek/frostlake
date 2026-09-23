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
import dev.frostlake.values.YearMonthInterval;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * An interval value's text, number and string reading, each by the fields of the type it is read AS — which
 * is the type the expression declares, not the unit a cell happened to be written in. Live, a column
 * declared {@code INTERVAL DAY(9)} prints {@code INTERVAL '25' HOUR} as {@code +1} and {@code INTERVAL '-3'
 * HOUR} as {@code -0}: the span in whole units of the LEADING field, truncated toward zero, its sign always
 * spelled.
 *
 * <pre>
 *   DAY(9)                +1   -1   +0   +100          YEAR(9)     +1   -2   +0
 *   HOUR(9)               +1   +25  -1                 MONTH(9)    +14  -14  +0
 *   MINUTE(9)             +1   +90  -5
 *   SECOND(9,9)           +1.000000000   -5.000000000
 *   DAY(9) TO SECOND(9)   +1 01:00:00.500000000
 * </pre>
 *
 * <p>A cast to a number reads the span in whole units of the TRAILING field ({@code INTERVAL '1' DAY::NUMBER}
 * is 1, a TIMESTAMP difference casts to its seconds), and a string compared with an interval is read in the
 * interval's own fields, refused in the account's words when it does not fit them.
 */
final class IntervalText {

    private static final BigInteger NANOS_PER_SECOND = BigInteger.valueOf(1_000_000_000L);
    private static final BigInteger NANOS_PER_MINUTE = NANOS_PER_SECOND.multiply(BigInteger.valueOf(60));
    private static final BigInteger NANOS_PER_HOUR = NANOS_PER_MINUTE.multiply(BigInteger.valueOf(60));
    private static final BigInteger NANOS_PER_DAY = NANOS_PER_HOUR.multiply(BigInteger.valueOf(24));
    private static final int MONTHS_PER_YEAR = 12;
    private static final int FRACTION_DIGITS = 9;
    private static final int LEADING_DIGITS = 9;

    private IntervalText() {
    }

    /**
     * The fields an interval cell is read as when nothing declares them: a literal's own field, and the
     * widest qualifier of its family otherwise.
     *
     * @param cell an interval cell
     * @return its qualifier
     */
    static IntervalQualifier ownQualifier(final Object cell) {
        if (cell instanceof IntervalLiteral) {
            return ((IntervalLiteral) cell).qualifier();
        }
        return cell instanceof YearMonthInterval ? IntervalQualifier.YEAR_TO_MONTH : IntervalQualifier.DAY_TO_SECOND;
    }

    /**
     * An interval value's text, read as the given fields, a trailing SECOND at nine fractional digits.
     *
     * @param cell      a day-time or year-month interval value
     * @param qualifier the fields to read it as
     * @return its text
     */
    static String render(final Object cell, final IntervalQualifier qualifier) {
        return render(cell, qualifier, FRACTION_DIGITS);
    }

    /**
     * An interval value's text, read as the given fields: the leading field's count, then each later field in
     * two digits — after a space when a DAY leads to an HOUR, after a colon otherwise — and a trailing SECOND's
     * fraction at the fractional precision, truncated (live-verified: {@code +1 02}, {@code +1 02:03},
     * {@code +2:03:04.000000000}, {@code +3:04.5} for a MINUTE(2) TO SECOND(1), {@code +1.250} for a
     * SECOND(2,3), {@code +1 02:03:04} for a DAY TO SECOND(0), {@code -1-02} for a YEAR TO MONTH). The fields
     * are printed by {@code IntervalFields.render}.
     *
     * @param cell                a day-time or year-month interval value
     * @param qualifier           the fields to read it as
     * @param fractionalPrecision the fractional digits a trailing SECOND shows
     * @return its text
     */
    static String render(final Object cell, final IntervalQualifier qualifier, final int fractionalPrecision) {
        return IntervalFields.render(cell, qualifier, qualifier.endsInSecond() ? fractionalPrecision : 0);
    }

    /**
     * The number an interval casts to: its span in units of the qualifier's TRAILING field.
     *
     * @param cell      a day-time or year-month interval value
     * @param qualifier the fields it is read as
     * @return the number
     */
    static BigDecimal amount(final Object cell, final IntervalQualifier qualifier) {
        if (cell instanceof YearMonthInterval) {
            final long months = ((YearMonthInterval) cell).months();
            return qualifier == IntervalQualifier.YEAR
                ? BigDecimal.valueOf(months).divide(BigDecimal.valueOf(MONTHS_PER_YEAR), FRACTION_DIGITS,
                    RoundingMode.HALF_UP).stripTrailingZeros()
                : BigDecimal.valueOf(months);
        }
        final BigDecimal nanos = new BigDecimal(IntervalCells.nanos(cell));
        final BigInteger unit;
        switch (qualifier) {
            case DAY:
                unit = NANOS_PER_DAY;
                break;
            case HOUR:
            case DAY_TO_HOUR:
                unit = NANOS_PER_HOUR;
                break;
            case MINUTE:
            case DAY_TO_MINUTE:
            case HOUR_TO_MINUTE:
                unit = NANOS_PER_MINUTE;
                break;
            default:
                unit = NANOS_PER_SECOND;
                break;
        }
        final BigDecimal amount = nanos.divide(new BigDecimal(unit), FRACTION_DIGITS, RoundingMode.HALF_UP);
        return amount.scale() > 0 ? amount.stripTrailingZeros() : amount;
    }

    /**
     * A string read as an interval of the given fields, for a comparison against one: live converts the
     * string to the interval's type ({@code INTERVAL '1' DAY = '1'} is TRUE, {@code INTERVAL '24' HOUR = '1'}
     * FALSE) and refuses text that does not fit it, naming the formats every subtype takes.
     *
     * @param text      the string
     * @param qualifier the interval's fields
     * @return the interval value
     */
    static Object parse(final String text, final IntervalQualifier qualifier) {
        return IntervalFields.parse(text, qualifier, LEADING_DIGITS, FRACTION_DIGITS);
    }
}
