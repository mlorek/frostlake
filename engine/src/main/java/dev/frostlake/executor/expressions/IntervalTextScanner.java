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

import java.math.BigInteger;

/**
 * The reader over one interval string (see {@link IntervalFields}): it walks the trimmed text field by
 * field and holds the sentences a fault earns, each echoing the text as it was written. The leading field
 * is measured before the rest is read, so an over-long number is the precision sentence even where the
 * text has no other field the type needs: {@code '86400000000000'} read as a DAY TO SECOND is refused for
 * its digits, {@code '90000'} for its format (live-verified).
 */
final class IntervalTextScanner {

    private static final int LATER_FIELD_DIGITS = 2;
    private static final int FRACTION_DIGITS = 9;

    private static final String DAY_TIME_FORMATS = "expected format is '<sign>D(p) HH24:MM:SS.F(fsp)' for subtype "
        + "DAY TO SECOND, '<sign>D(p) HH24:MM' for subtype DAY TO MINUTE, '<sign>D(p) HH24' for subtype DAY TO HOUR, "
        + "'<sign>D(p)' for subtype DAY, '<sign>H(p):MM:SS.F(fsp)' for subtype HOUR TO SECOND, '<sign>H(p):MM' for "
        + "subtype HOUR TO MINUTE, '<sign>H(p)' for subtype HOUR, '<sign>M(p):SS.F(fsp)' for subtype MINUTE TO "
        + "SECOND, '<sign>M(p)' for subtype MINUTE, or '<sign>S(p).F(fsp)' for subtype SECOND";
    private static final String YEAR_MONTH_FORMATS = "expected format is '<sign>Y(p)-MM' for subtype YEAR TO MONTH, "
        + "'<sign>Y(p)' for subtype YEAR, or '<sign>M(p)' for subtype MONTH";

    private final String text;
    private final String written;
    private final boolean dayTime;
    private int position;

    /**
     * @param text    the trimmed string
     * @param written the string as written, for the sentences
     * @param dayTime whether it is read as a day-time interval rather than a year-month one
     */
    IntervalTextScanner(final String text, final String written, final boolean dayTime) {
        this.text = text;
        this.written = written;
        this.dayTime = dayTime;
    }

    /** @return whether a leading minus was read; a leading plus is read and dropped */
    boolean sign() {
        if (position < text.length() && (text.charAt(position) == '+' || text.charAt(position) == '-')) {
            position++;
            return text.charAt(position - 1) == '-';
        }
        return false;
    }

    /** @return the run of digits at the current position, possibly empty */
    String digits() {
        final int start = position;
        while (position < text.length() && Character.isDigit(text.charAt(position))) {
            position++;
        }
        return text.substring(start, position);
    }

    /**
     * A field after the leading one: its separator, then one or two digits.
     *
     * @param separator the character before the field
     * @return the field's value
     */
    int laterField(final char separator) {
        if (position >= text.length() || text.charAt(position) != separator) {
            throw invalidFormat();
        }
        position++;
        final String field = digits();
        if (field.isEmpty()) {
            throw invalidFormat();
        }
        if (field.length() > LATER_FIELD_DIGITS) {
            throw pastPrecision();
        }
        return Integer.parseInt(field);
    }

    /**
     * An optional fraction of a second, in at most {@code digits} digits; none is allowed at zero digits.
     *
     * @param digits the fractional digits the type keeps
     * @return the fraction in nanoseconds
     */
    BigInteger fraction(final int digits) {
        if (position >= text.length() || text.charAt(position) != '.' || digits == 0) {
            return BigInteger.ZERO;
        }
        position++;
        final String fraction = digits();
        if (fraction.isEmpty()) {
            throw invalidFormat();
        }
        if (fraction.length() > digits) {
            throw pastPrecision();
        }
        return new BigInteger((fraction + "000000000").substring(0, FRACTION_DIGITS));
    }

    /** Refuses anything left over. */
    void end() {
        if (position != text.length()) {
            throw invalidFormat();
        }
    }

    /** @return the refusal of a text that fits no subtype's format */
    RuntimeException invalidFormat() {
        return dayTime
            ? new RuntimeException("Day-Time Interval '" + written + "' is invalid, " + DAY_TIME_FORMATS)
            : new RuntimeException("Year-Month Interval '" + written + "' is invalid, " + YEAR_MONTH_FORMATS);
    }

    /** @return the refusal of a field written in more digits than the type keeps */
    RuntimeException pastPrecision() {
        return dayTime
            ? new RuntimeException("Day-Time Interval is '" + written + "' invalid, value of leading or fractional "
                + "second field is greater than specified precision/fsp")
            : new RuntimeException("Year-Month Interval '" + written + "' is invalid, value of leading field is "
                + "greater than specified precision");
    }
}
