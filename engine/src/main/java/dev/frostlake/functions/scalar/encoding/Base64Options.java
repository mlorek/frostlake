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

package dev.frostlake.functions.scalar.encoding;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;

/**
 * The optional arguments the base64 family carries beyond its input.
 *
 * <p>{@code BASE64_ENCODE} takes up to three: the value, a MAX LINE LENGTH that wraps the encoded
 * text, and an ALPHABET whose two characters stand in for the standard {@code +} and {@code /}. The
 * decoders take the alphabet as their second argument so a custom encoding can be read back —
 * {@code BASE64_DECODE_BINARY(BASE64_ENCODE(b, 0, '$%'), '$%')} round-trips, while decoding the same
 * text with the standard alphabet is an error, because {@code $} and {@code %} are not base64.
 *
 * <p>A line length of 0 means no wrapping, which is the default. Wrapping inserts a newline every N
 * characters with none left trailing, so a 14-group encoding at width 8 carries 13 newlines.
 */
public final class Base64Options {

    /** The characters a custom alphabet replaces, in order. */
    private static final String STANDARD = "+/";

    /** The 62 characters every base64 alphabet shares — a custom character may not collide with one. */
    private static final String SHARED_CHARACTERS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private Base64Options() {
    }

    /** The alphabet argument at {@code index}, or the standard {@code +/} when it is not given. */
    public static String alphabetOf(final List<Object> args, final int index) {
        if (args.size() <= index || args.get(index) == null) {
            return STANDARD;
        }
        final String given = args.get(index).toString();
        if (given.isEmpty()) {
            return STANDARD;
        }
        rejectUnusableAlphabet(given);
        return given;
    }

    /**
     * A custom alphabet gives the characters that stand in for {@code +}, {@code /} and the {@code =}
     * padding, in that order — so at most THREE, and each of them has to be a character base64 does not
     * already use. Live refuses both ways it can go wrong, and the two sentences are different:
     *
     * <pre>
     *   '$%^&amp;'   String '$%^&amp;' is too long and would be truncated in 'Base64 custom characters'
     *   'abc'    Invalid Base64 custom alphabet or padding characters: 'abc'
     *   '12'     the same — a digit is already in the alphabet, so the encoding would be ambiguous
     *   '$$'     the same — the two replacements collide with EACH OTHER
     *   '=='     the same, for the same reason; a SINGLE '=' as the third character is fine
     * </pre>
     *
     * <p>So the test is not on the characters in isolation but on the finished 65-character alphabet:
     * it must hold no character twice. That is why {@code '+/'} passes (each character replaces
     * itself) while {@code 'AB'} does not.
     *
     * <p>Refused at ROW time, measured: the same call over an EMPTY table is accepted, so live is not
     * deciding this while it compiles.
     */
    private static void rejectUnusableAlphabet(final String alphabet) {
        if (alphabet.length() > 3) {
            throw new RuntimeException("String '" + alphabet
                + "' is too long and would be truncated in 'Base64 custom characters'");
        }
        final StringBuilder full = new StringBuilder(SHARED_CHARACTERS)
            .append(alphabet.length() >= 1 ? alphabet.charAt(0) : '+')
            .append(alphabet.length() >= 2 ? alphabet.charAt(1) : '/')
            .append(alphabet.length() >= 3 ? alphabet.charAt(2) : '=');
        for (int i = SHARED_CHARACTERS.length(); i < full.length(); i++) {
            if (full.indexOf(String.valueOf(full.charAt(i))) != i) {
                throw new RuntimeException(
                    "Invalid Base64 custom alphabet or padding characters: '" + alphabet + "'");
            }
        }
    }

    /**
     * The max-line-length argument at {@code index}, or 0 — no wrapping — when it is not given.
     *
     * <p>A fractional length is ROUNDED rather than truncated, half away from zero, which is visible
     * in the output: a length of 8.7 wraps at NINE characters and one of 1.5 at two. Anything that
     * rounds to zero or below wraps nothing — a negative length reaching this point is not an error,
     * it is no wrapping. A length that does not fit a 32-bit integer IS an error, and live names the
     * ROUNDED value in it.
     *
     * <p>The compile-time half of the rule lives in the expression walk, which refuses an integral
     * numeric LITERAL outside {@code [0, 2147483647]} before a row is read. The halves do not
     * overlap: such a literal never reaches here, and everything else is only ever judged here.
     */
    public static int lineLengthOf(final List<Object> args, final int index) {
        if (args.size() <= index || args.get(index) == null) {
            return 0;
        }
        final BigInteger rounded = new BigDecimal(args.get(index).toString())
            .setScale(0, RoundingMode.HALF_UP).toBigInteger();
        if (rounded.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                || rounded.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) {
            throw new RuntimeException("Numeric value '" + rounded + "' is out of range");
        }
        return rounded.intValue();
    }

    /** An alphabet this long names its own PADDING character in the third position. */
    private static final int THIRD_SLOT = 3;

    /** A character base64 cannot read, used to make an out-of-alphabet '=' fail as live does. */
    private static final char NOT_BASE64 = '!';

    /** Standard base64 output rewritten into the caller's alphabet. */
    public static String toCustomAlphabet(final String standard, final String alphabet) {
        if (STANDARD.equals(alphabet)) {
            return standard;
        }
        final StringBuilder out = new StringBuilder(standard.length());
        for (int i = 0; i < standard.length(); i++) {
            final char c = standard.charAt(i);
            if (c == '+' && alphabet.length() >= 1) {
                out.append(alphabet.charAt(0));
            } else if (c == '/' && alphabet.length() >= 2) {
                out.append(alphabet.charAt(1));
            } else if (c == '=' && alphabet.length() >= THIRD_SLOT) {
                // The third slot stands in for the PADDING, and live really does write it:
                // BASE64_ENCODE('a', 0, '$%^') is YQ^^, not YQ==. A two-character alphabet leaves the
                // padding alone, which is why '$%=' and the default are indistinguishable.
                out.append(alphabet.charAt(2));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The caller's alphabet rewritten back to standard base64, ready to decode. */
    public static String toStandardAlphabet(final String text, final String alphabet) {
        if (STANDARD.equals(alphabet)) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (alphabet.length() >= 1 && c == alphabet.charAt(0)) {
                out.append('+');
            } else if (alphabet.length() >= 2 && c == alphabet.charAt(1)) {
                out.append('/');
            } else if (alphabet.length() >= THIRD_SLOT && c == alphabet.charAt(2)) {
                out.append('=');
            } else if (c == '=' && alphabet.length() >= THIRD_SLOT) {
                // Naming a custom pad makes '=' ILLEGAL rather than harmless: live refuses
                // BASE64_DECODE_STRING('YQ==', '$%^') as "not a legal base64-encoded value" even
                // though '=' is the standard padding. Passing it through would decode it happily, so
                // it becomes a character base64 has no reading for and the decoder refuses — the same
                // way every other character outside the alphabet already does.
                out.append(NOT_BASE64);
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The encoded text broken into lines of {@code maxLineLength}, or unchanged when that is 0. */
    public static String wrap(final String encoded, final int maxLineLength) {
        if (maxLineLength <= 0 || encoded.length() <= maxLineLength) {
            return encoded;
        }
        final StringBuilder out = new StringBuilder(encoded.length() + encoded.length() / maxLineLength);
        for (int start = 0; start < encoded.length(); start += maxLineLength) {
            if (start > 0) {
                out.append('\n');
            }
            out.append(encoded, start, Math.min(start + maxLineLength, encoded.length()));
        }
        return out.toString();
    }

    /** The refusal for text that is not base64 once its alphabet has been normalised. */
    public static RuntimeException notBase64(final String text) {
        return new RuntimeException(
            "The following string is not a legal base64-encoded value: '" + text + "'");
    }
}
