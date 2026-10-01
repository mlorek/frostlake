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

package dev.frostlake.executor.copy;

import dev.frostlake.functions.scalar.AutoTemporalParser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * The type INFER_SCHEMA reads off one staged value.
 *
 * <p>A <b>CSV field</b> is tried, in order, as
 * <ul>
 *   <li>a NUMBER — an optional sign, digits and an optional point ({@code 1.}, {@code .5} and {@code +7} are
 *       numbers, {@code  5} with a space is not, a point alone is a zero). Its integer digits are counted
 *       without leading zeros and at least one, its scale is every digit after the point, trailing zeros kept:
 *       {@code 007} is NUMBER(1, 0), {@code 0.10} NUMBER(3, 2). A zero is NUMBER(1, 0) however many zeros it is
 *       written with, and a number of more than 38 digits is a REAL;</li>
 *   <li>a REAL — scientific notation with a digit before the exponent ({@code 1e2}, {@code -1.0e+10},
 *       {@code 1.e5}; not {@code .e5}), a hexadecimal integer ({@code 0x1F}), {@code NaN} or {@code inf}; one
 *       that overflows a double ({@code 1e400}) is TEXT, one too small for it ({@code 1e-400}) still a REAL;</li>
 *   <li>a BOOLEAN — {@code true}, {@code false}, {@code t}, {@code f}, {@code yes}, {@code no}, {@code y},
 *       {@code n}, {@code on} and {@code off} in any case ({@code 1} and {@code 0} are numbers);</li>
 *   <li>a DATE or a TIMESTAMP_NTZ — the date forms {@code YYYY-MM-DD}, {@code MM/DD/YYYY} and
 *       {@code DD-MON-YYYY}, a timestamp being one of the first two followed by a time — its fraction of any
 *       length — and an optional zone, of any flavour;</li>
 *   <li>a TIME — {@code HH:MI}, {@code HH:MI:SS} and a fraction of any length, each part of one or two digits,
 *       on a twenty-four-hour clock or on a twelve-hour one followed by {@code AM} or {@code PM}; a zone only
 *       after a fraction's point ({@code 10:00:00.1+07:00});</li>
 *   <li>and TEXT otherwise.</li>
 * </ul>
 *
 * <p>A <b>JSON value</b> is typed by what it is: a number written with an exponent, or of more than 38 digits,
 * is a REAL and any other a NUMBER whose written scale is kept, zeros included ({@code 0.00} is NUMBER(3, 2));
 * a string is a DATE, a TIME or a TIMESTAMP_NTZ when it reads as one and TEXT otherwise — never a NUMBER or a
 * BOOLEAN, however it is spelled.
 */
public final class StagedValueKinds {

    /** The widest NUMBER. */
    private static final int MAX_PRECISION = 38;

    /** The widest fraction a TIME carries. */
    private static final int MAX_FRACTION_DIGITS = 9;

    /** The words a BOOLEAN field is written with. */
    private static final Set<String> BOOLEAN_WORDS = new HashSet<String>(Arrays.asList(
        "TRUE", "FALSE", "T", "F", "YES", "NO", "Y", "N", "ON", "OFF"));

    /** The words a REAL field may be besides a number. */
    private static final Set<String> NON_FINITE_WORDS = new HashSet<String>(Arrays.asList(
        "NAN", "INF", "+INF", "-INF", "INFINITY", "+INFINITY", "-INFINITY"));

    private StagedValueKinds() {
    }

    /**
     * The type a CSV field reads as.
     *
     * @param field the field once NULL_IF and EMPTY_FIELD_AS_NULL are applied; null for a NULL
     * @return the field's type, or null for a NULL
     */
    public static InferredType ofCsvField(final String field) {
        if (field == null) {
            return null;
        }
        final InferredType number = plainNumber(field, true);
        if (number != null) {
            return number;
        }
        if (isReal(field)) {
            return InferredType.of(InferredKind.REAL);
        }
        if (BOOLEAN_WORDS.contains(field.toUpperCase(Locale.ROOT))) {
            return InferredType.of(InferredKind.BOOLEAN);
        }
        return temporalOrText(field);
    }

    /**
     * The type a JSON string reads as: a DATE, a TIME, a TIMESTAMP_NTZ or TEXT.
     *
     * @param value the string's content
     * @return the type
     */
    public static InferredType ofJsonString(final String value) {
        return temporalOrText(value);
    }

    /**
     * The type a JSON number reads as, from the number as written.
     *
     * @param literal the number token's text
     * @return a NUMBER, or a REAL for exponent notation or more than 38 digits
     */
    public static InferredType ofJsonNumber(final String literal) {
        if (literal.indexOf('e') >= 0 || literal.indexOf('E') >= 0) {
            return InferredType.of(InferredKind.REAL);
        }
        final InferredType number = plainNumber(literal, false);
        return number != null ? number : InferredType.of(InferredKind.REAL);
    }

    /**
     * Whether a JSON number token is written the way a REAL reads — the name a refusal gives a record that is
     * such a number rather than an object.
     *
     * @param literal the number token's text
     * @return whether it reads as a REAL
     */
    public static boolean isJsonReal(final String literal) {
        return ofJsonNumber(literal).kind() == InferredKind.REAL;
    }

    /**
     * A plain decimal: an optional sign, digits and an optional point. Null when the text is not one; a REAL
     * when it carries more than 38 digits and a double holds it, TEXT when not even a double does.
     *
     * @param text the text
     * @param zeroIsWhole whether a zero reads as NUMBER(1, 0) whatever its written scale, as a CSV field does
     */
    private static InferredType plainNumber(final String text, final boolean zeroIsWhole) {
        int i = 0;
        if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            i++;
        }
        final int integerStart = i;
        while (i < text.length() && isDigit(text.charAt(i))) {
            i++;
        }
        final int integerEnd = i;
        int fractionDigits = 0;
        boolean nonZeroFraction = false;
        final boolean point = i < text.length() && text.charAt(i) == '.';
        if (point) {
            i++;
            final int fractionStart = i;
            while (i < text.length() && isDigit(text.charAt(i))) {
                if (text.charAt(i) != '0') {
                    nonZeroFraction = true;
                }
                i++;
            }
            fractionDigits = i - fractionStart;
        }
        // A point alone is a number too — '.', '-.' and '+.' read as zero — but a sign alone is not.
        if (i != text.length() || integerEnd - integerStart + fractionDigits == 0 && !point) {
            return null;
        }
        int significant = integerStart;
        while (significant < integerEnd && text.charAt(significant) == '0') {
            significant++;
        }
        final int integerDigits = integerEnd - significant;
        if (zeroIsWhole && integerDigits == 0 && !nonZeroFraction) {
            return InferredType.number(1, 0);
        }
        final int wholeDigits = Math.max(1, zeroIsWhole ? integerDigits : integerEnd - integerStart);
        if (wholeDigits + fractionDigits > MAX_PRECISION) {
            return finiteDouble(text) ? InferredType.of(InferredKind.REAL) : InferredType.of(InferredKind.TEXT);
        }
        return InferredType.number(wholeDigits, fractionDigits);
    }

    /** Whether a field reads as a REAL that is not a plain decimal. */
    private static boolean isReal(final String text) {
        if (NON_FINITE_WORDS.contains(text.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return isHexInteger(text) || isScientific(text) && finiteDouble(text);
    }

    /** An optional sign, {@code 0x} and hexadecimal digits. */
    private static boolean isHexInteger(final String text) {
        int i = 0;
        if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            i++;
        }
        if (i + 2 >= text.length() || text.charAt(i) != '0'
                || text.charAt(i + 1) != 'x' && text.charAt(i + 1) != 'X') {
            return false;
        }
        for (int h = i + 2; h < text.length(); h++) {
            if (Character.digit(text.charAt(h), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    /** A plain decimal with at least one digit ({@code .e5} is no number), followed by an exponent. */
    private static boolean isScientific(final String text) {
        int exponent = text.indexOf('e');
        if (exponent < 0) {
            exponent = text.indexOf('E');
        }
        if (exponent <= 0 || plainNumber(text.substring(0, exponent), false) == null
                || !hasDigit(text.substring(0, exponent))) {
            return false;
        }
        int i = exponent + 1;
        if (i < text.length() && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
            i++;
        }
        final int digitsStart = i;
        while (i < text.length() && isDigit(text.charAt(i))) {
            i++;
        }
        return i == text.length() && i > digitsStart;
    }

    /** Whether a double holds the number without overflowing; one too small for it reads as zero, still a REAL. */
    private static boolean finiteDouble(final String text) {
        try {
            return !Double.isInfinite(Double.parseDouble(text));
        } catch (final NumberFormatException notNumeric) {
            return false;
        }
    }

    private static boolean hasDigit(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (isDigit(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** A DATE, a TIMESTAMP_NTZ or a TIME when the text reads as one, TEXT otherwise. */
    private static InferredType temporalOrText(final String text) {
        if (text.isEmpty() || !text.equals(text.trim())) {
            return InferredType.of(InferredKind.TEXT);
        }
        if (!isDayMonthYearWithTime(text) && AutoTemporalParser.parseDateTime(withNineFractionDigits(text)) != null) {
            // A date form never carries a colon and a time always does.
            return InferredType.of(text.indexOf(':') >= 0 ? InferredKind.TIMESTAMP : InferredKind.DATE);
        }
        if (isTime(text)) {
            return InferredType.of(InferredKind.TIME);
        }
        return InferredType.of(InferredKind.TEXT);
    }

    /**
     * The text with the fraction of its seconds cut to nine digits, or dropped when its point carries none: a
     * timestamp reads the same with any number of fraction digits ({@code 2020-01-01 10:00:00.1234567891}) or
     * none after the point ({@code 2020-01-01 10:00:00.}).
     */
    private static String withNineFractionDigits(final String text) {
        final int point = text.indexOf('.');
        if (point < 1 || !isDigit(text.charAt(point - 1)) || text.lastIndexOf(':', point) < 0) {
            return text;
        }
        int end = point + 1;
        while (end < text.length() && isDigit(text.charAt(end))) {
            end++;
        }
        final int digits = end - point - 1;
        if (digits == 0) {
            return text.substring(0, point) + text.substring(end);
        }
        return digits > MAX_FRACTION_DIGITS
            ? text.substring(0, point + 1 + MAX_FRACTION_DIGITS) + text.substring(end) : text;
    }

    /**
     * A {@code DD-MON-YYYY} date followed by anything, which no timestamp form begins with: {@code 31-JAN-2020} is
     * a DATE and {@code 31-JAN-2020 10:00} is TEXT.
     */
    private static boolean isDayMonthYearWithTime(final String text) {
        int i = 0;
        while (i < text.length() && isDigit(text.charAt(i))) {
            i++;
        }
        if (i < 1 || i > 2 || i + 4 >= text.length() || text.charAt(i) != '-' || text.charAt(i + 4) != '-') {
            return false;
        }
        for (int m = i + 1; m < i + 4; m++) {
            if (!Character.isLetter(text.charAt(m))) {
                return false;
            }
        }
        int year = i + 5;
        while (year < text.length() && isDigit(text.charAt(year))) {
            year++;
        }
        return year < text.length();
    }

    /**
     * A time of day: {@code HH24:MI}, {@code HH24:MI:SS} and a fraction of any length after the point, the point
     * alone too — each part one or two digits — or the same with {@code HH12} and {@code AM} or {@code PM} after
     * any number of spaces, in any case. A fraction's point may be followed, after any spaces, by a zone: {@code Z}
     * or a sign, one or two digits, a colon and one or two digits ({@code 10:00:00.1+07:00}); a zone anywhere else
     * makes it TEXT ({@code 10:00:00+07:00}, {@code 10:00:00.1+07:00 PM}).
     */
    private static boolean isTime(final String text) {
        final String upper = text.toUpperCase(Locale.ROOT);
        if (upper.endsWith("AM") || upper.endsWith("PM")) {
            int end = text.length() - 2;
            while (end > 0 && text.charAt(end - 1) == ' ') {
                end--;
            }
            return isClockTime(text.substring(0, end), true);
        }
        return isClockTime(text, false);
    }

    /** {@code H:M[:S[.F][zone]]}, the zone only on a twenty-four-hour clock, as {@code isTime} describes. */
    private static boolean isClockTime(final String text, final boolean twelveHour) {
        final int[] at = {0};
        final int hour = digitsAt(text, at);
        if (hour < 0 || (twelveHour ? hour < 1 || hour > 12 : hour > 23) || !charAt(text, at, ':')) {
            return false;
        }
        final int minute = digitsAt(text, at);
        if (minute < 0 || minute > 59) {
            return false;
        }
        if (at[0] == text.length()) {
            return true;
        }
        if (!charAt(text, at, ':')) {
            return false;
        }
        final int second = digitsAt(text, at);
        if (second < 0 || second > 59) {
            return false;
        }
        if (at[0] == text.length()) {
            return true;
        }
        if (!charAt(text, at, '.')) {
            return false;
        }
        while (at[0] < text.length() && isDigit(text.charAt(at[0]))) {
            at[0]++;
        }
        return at[0] == text.length() || !twelveHour && isZone(text, at[0]);
    }

    /** Spaces, then {@code Z} or a sign, one or two digits, a colon and one or two digits, to the end. */
    private static boolean isZone(final String text, final int from) {
        final int[] at = {from};
        while (at[0] < text.length() && text.charAt(at[0]) == ' ') {
            at[0]++;
        }
        if (at[0] < text.length() && text.charAt(at[0]) == 'Z') {
            return at[0] + 1 == text.length();
        }
        if (at[0] >= text.length() || text.charAt(at[0]) != '+' && text.charAt(at[0]) != '-') {
            return false;
        }
        at[0]++;
        return digitsAt(text, at) >= 0 && charAt(text, at, ':') && digitsAt(text, at) >= 0 && at[0] == text.length();
    }

    /** The value of the one or two digits at the cursor, moved past them; -1 when there are none or more. */
    private static int digitsAt(final String text, final int[] at) {
        final int start = at[0];
        while (at[0] < text.length() && isDigit(text.charAt(at[0]))) {
            at[0]++;
        }
        final int count = at[0] - start;
        return count < 1 || count > 2 ? -1 : Integer.parseInt(text.substring(start, at[0]));
    }

    /** Whether the cursor is at {@code expected}, moved past it when it is. */
    private static boolean charAt(final String text, final int[] at, final char expected) {
        if (at[0] < text.length() && text.charAt(at[0]) == expected) {
            at[0]++;
            return true;
        }
        return false;
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }
}
