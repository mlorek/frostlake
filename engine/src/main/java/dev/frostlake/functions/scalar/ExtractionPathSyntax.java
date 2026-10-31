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
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * The reading {@code GET_PATH} gives a path written as a literal before the statement runs — stricter than
 * the one a path is given while it runs ({@link ExtractionPath}), and the one that says where the fault is
 * (live-verified):
 * <pre>
 *   " a"  ".a"  "-a"  "a b"  "a[0]b"  "a[0] "     invalid token at position N       the character itself
 *   "a-b"  "1a"  "a.9"  "a]"                      invalid field at position N       where the name starts
 *   "a."  "a:"  "a. "                             dangling dot at position N        past the separator
 *   "a..b"  "a.[0]"                               empty (unquoted) field name at position N   likewise
 *   "a["  "a[0"  "a[0 1]"                         dangling square bracket at position N   past the bracket
 *   "a[]"  "a[ ]"                                 empty square brackets at position N     likewise
 *   "a[x]"  "a[+1]"  "a[-1]"  "a[1.0]"            invalid number at position N      past its first character
 * </pre>
 * and a quoted name nothing closes is a dangling quote at the position past the quote. A bare name here is
 * ASCII letters, digits, {@code _ $ @} and the characters of Latin-1 past ASCII but U+00C0, U+00C1 and U+00F5 to
 * U+00FF — the values no byte of UTF-8 takes — with no digit first (every one of the 128 measured, first in the
 * name and later; everything above Latin-1 is refused too); it runs up to the
 * next separator, bracket, space or tab and is judged whole. Spaces and tabs may follow a name, a separator
 * and a bracket's contents, but not a closing bracket. A position past 2,147,483,647 is refused by its own
 * sentence.
 */
public final class ExtractionPathSyntax {

    private final String text;
    private int pos;

    private ExtractionPathSyntax(final String text) {
        this.text = text;
    }

    /**
     * Judges a path written as a literal.
     *
     * @param text the path
     * @return the refusal, or null when the path is well-formed
     */
    public static String refusal(final String text) {
        return new ExtractionPathSyntax(text).judge();
    }

    private String judge() {
        if (text.isEmpty()) {
            return null;
        }
        final char first = text.charAt(0);
        boolean bracketed = first == '[';
        String fault;
        if (bracketed) {
            fault = bracket();
        } else if (first == '"') {
            fault = quoted();
        } else if (isNamePart(first)) {
            fault = name();
        } else {
            fault = at("invalid token", 0);
        }
        while (fault == null) {
            if (!bracketed) {
                skipBlanks();
            }
            if (pos == text.length()) {
                return null;
            }
            final char c = text.charAt(pos);
            bracketed = c == '[';
            if (bracketed) {
                fault = bracket();
            } else if (ExtractionPathReader.isSeparator(c)) {
                fault = separated();
            } else {
                fault = at("invalid token", pos);
            }
        }
        return fault;
    }

    private String separated() {
        pos++;
        final int past = pos;
        skipBlanks();
        if (pos == text.length()) {
            return at("dangling dot", past);
        }
        final char c = text.charAt(pos);
        if (ExtractionPathReader.isSeparator(c) || c == '[') {
            return at("empty (unquoted) field name", past);
        }
        return c == '"' ? quoted() : name();
    }

    /** A bare name: everything up to the next separator, bracket or blank, judged whole. */
    private String name() {
        final int start = pos;
        while (pos < text.length() && !isNameEnd(text.charAt(pos))) {
            pos++;
        }
        boolean valid = !ExtractionPathReader.isDigit(text.charAt(start));
        for (int i = start; valid && i < pos; i++) {
            valid = isNamePart(text.charAt(i));
        }
        return valid ? null : at("invalid field", start);
    }

    private String quoted() {
        final int end = ExtractionPathReader.closingQuote(text, pos);
        if (end < 0) {
            return at("dangling quote", pos + 1);
        }
        pos = end + 1;
        return null;
    }

    private String bracket() {
        pos++;
        final int past = pos;
        skipBlanks();
        if (pos == text.length()) {
            return at("dangling square bracket", past);
        }
        final char c = text.charAt(pos);
        if (c == ']') {
            return at("empty square brackets", past);
        }
        String digits = null;
        if (c == '"' || c == '\'') {
            final String fault = quoted();
            if (fault != null) {
                return fault;
            }
        } else {
            final int start = pos;
            boolean number = true;
            while (pos < text.length() && text.charAt(pos) != ']'
                    && !ExtractionPathReader.isBlank(text.charAt(pos))) {
                number = number && ExtractionPathReader.isDigit(text.charAt(pos));
                pos++;
            }
            if (!number) {
                return at("invalid number", start + 1);
            }
            digits = text.substring(start, pos);
        }
        skipBlanks();
        if (pos == text.length() || text.charAt(pos) != ']') {
            return at("dangling square bracket", past);
        }
        pos++;
        return digits == null ? null : offLimits(digits);
    }

    /** The refusal of a position no array can count, which names the position where the path is expected. */
    private static String offLimits(final String digits) {
        final BigInteger position = new BigInteger(digits);
        return position.bitLength() < Integer.SIZE ? null : offLimitsIndex(position);
    }

    /**
     * The refusal of an array index no array counts, the index grouped by thousands as the path names it:
     * "Invalid extraction path '-3,000,000,000': array index -3,000,000,000 is off limits; must be between 0 and
     * 2,147,483,647." (live-verified).
     *
     * @param index the index, below 0 or past 2,147,483,647
     * @return the refusal
     */
    public static String offLimitsIndex(final BigInteger index) {
        final String grouped = new DecimalFormat("#,###", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(index);
        return "Invalid extraction path '" + grouped + "': array index " + grouped
            + " is off limits; must be between 0 and 2,147,483,647.";
    }

    private String at(final String fault, final int position) {
        return "Invalid extraction path '" + text + "': " + fault + " at position " + position + ".";
    }

    private void skipBlanks() {
        while (pos < text.length() && ExtractionPathReader.isBlank(text.charAt(pos))) {
            pos++;
        }
    }

    private static boolean isNameEnd(final char c) {
        return ExtractionPathReader.isSeparator(c) || c == '[' || ExtractionPathReader.isBlank(c);
    }

    private static boolean isNamePart(final char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
            || c == '_' || c == '$' || c == '@' || (c >= 0x80 && c <= 0xBF) || (c >= 0xC2 && c <= 0xF4);
    }
}
