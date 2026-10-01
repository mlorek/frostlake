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

package dev.frostlake.functions.scalar;

import java.math.BigInteger;

/**
 * The HEXADECIMAL numbers Snowflake's JSON reader accepts, rewritten to the decimal a JSON parser reads.
 * A token is {@code 0x} or {@code 0X} followed by hex digits, optionally a hex fraction and a binary
 * exponent, and it may carry a leading plus:
 *
 * <pre>
 *   0x10, 0X1F, 0xABCDEF, +0x10   the integer, exactly — 0x10000000000000000 is 18446744073709551616
 *   +0x10                         a leading plus belongs to the token; a leading minus never does
 *   0x1.5, 0x1., 0x.5, 0x1p3      a DOUBLE, the C99 hex float: 1.3125, 1.0, 0.3125, 8.0
 * </pre>
 *
 * <p>What live refuses is left as written, for {@code JsonFaultReader} to name: a leading minus
 * ({@code -0x10}), a token running into other characters ({@code 0x10x}, {@code 00x10}, {@code 0x1.5.5}),
 * {@code 0x} with no digits, an exponent with no digits, and an integer past the signed 128-bit range
 * ({@code 0x80000000000000000000000000000000}) — all live-verified.
 */
public final class JsonHexNumbers {

    /** The greatest hex integer the reader converts: 2^127 - 1. */
    private static final BigInteger LARGEST = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

    private JsonHexNumbers() {
    }

    /**
     * The document with every hex number outside its strings rewritten to decimal.
     *
     * @param text the document as written, or null
     * @return the rewritten document, or the same text when it holds no hex number
     */
    public static String normalize(final String text) {
        if (text == null || text.indexOf('x') < 0 && text.indexOf('X') < 0) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            final int end = tokenEnd(text, i);
            if (end < 0) {
                out.append(c);
                continue;
            }
            final String decimal = decimalOf(text.substring(i, end));
            if (decimal == null) {
                out.append(c);
                continue;
            }
            out.append(decimal);
            i = end - 1;
        }
        return out.toString();
    }

    /**
     * Where the hex token starting at {@code start} ends, or -1 when none starts there: the character
     * before it must not continue a number of its own, so neither {@code 00x10} nor {@code -0x10} is one.
     */
    private static int tokenEnd(final String text, final int start) {
        final int digitsAt = text.charAt(start) == '+' ? start + 1 : start;
        if (digitsAt + 1 >= text.length() || text.charAt(digitsAt) != '0'
                || text.charAt(digitsAt + 1) != 'x' && text.charAt(digitsAt + 1) != 'X') {
            return -1;
        }
        if (start > 0 && continuesANumber(text.charAt(start - 1))) {
            return -1;
        }
        int at = digitsAt + 2;
        while (at < text.length() && isHexDigit(text.charAt(at))) {
            at++;
        }
        if (at < text.length() && text.charAt(at) == '.') {
            at++;
            while (at < text.length() && isHexDigit(text.charAt(at))) {
                at++;
            }
        }
        if (at < text.length() && (text.charAt(at) == 'p' || text.charAt(at) == 'P')) {
            at++;
            if (at < text.length() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                at++;
            }
            final int digits = at;
            while (at < text.length() && text.charAt(at) >= '0' && text.charAt(at) <= '9') {
                at++;
            }
            if (at == digits) {
                return -1;   // an exponent with no digits is the reader's own fault to name
            }
        }
        return at < text.length() && continuesANumber(text.charAt(at)) ? -1 : at;
    }

    /**
     * The decimal a hex token stands for: an exact integer, or a DOUBLE written in scientific notation, as
     * a hex fraction or a binary exponent makes it. Null for a token live refuses.
     */
    private static String decimalOf(final String written) {
        final String token = written.startsWith("+") ? written.substring(1) : written;
        final String digits = token.substring(2);
        if (digits.indexOf('.') < 0 && digits.indexOf('p') < 0 && digits.indexOf('P') < 0) {
            if (digits.isEmpty()) {
                return null;
            }
            final BigInteger value = new BigInteger(digits, 16);
            return value.compareTo(LARGEST) > 0 ? null : value.toString();
        }
        final String floating = digits.indexOf('p') < 0 && digits.indexOf('P') < 0 ? token + "p0" : token;
        final double value = Double.parseDouble(floating);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return null;
        }
        // Written in scientific notation, which is what marks the value as a DOUBLE rather than a decimal.
        final String plain = Double.toString(value);
        return plain.indexOf('E') >= 0 || plain.indexOf('e') >= 0 ? plain : plain + "E0";
    }

    private static boolean isHexDigit(final char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    /** The characters a number runs through, which therefore cannot stand beside a hex token. */
    private static boolean continuesANumber(final char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '+' || c == '-';
    }
}
