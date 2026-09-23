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

package dev.frostlake.functions.scalar.semistructured;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Names the first fault in a JSON document the way Snowflake does. It DECIDES NOTHING — the parse
 * itself is still the reader's, and this only runs once that reader has failed, to say what went
 * wrong. Frostlake used to answer "Invalid JSON: &lt;the whole document&gt;" for every fault alike.
 *
 * <p>The vocabulary, measured a fault at a time:
 *
 * <pre>
 *   unknown keyword "cdefg", pos 6                    a bare word that names no JSON value
 *   misplaced }, pos 5        misplaced ], pos 1      a closer where a value or a key belonged
 *   misplaced comma, pos 8                            two commas running, inside an object
 *   incomplete object value, pos 7                    the document ended mid-object
 *   incomplete array value, pos 5                     … or mid-array
 *   unfinished string, pos 5                          … or mid-string
 *   unterminated string, line 2, pos 0                a raw line feed inside a string (pos 3 for a carriage
 *                                                     return, which counts as a column)
 *   bad escape sequence in the string, pos 5          a backslash before a carriage return
 *   duplicate object attribute "abc", pos 14          the SAME object naming one key twice
 *   missing comma, pos 8       missing colon, pos 6   two members, or a key and its value, run on
 *   invalid character outside of a string: '#', pos 1
 *   stray minus sign, pos 2    no number after a sign, pos 2
 *   missing decimal exponent digits: '1e+', pos 4
 *   garbage in the numeric literal: 1a , pos 3        the literal runs through letters, points and
 *                                                     signs (2024-01-01), then the character after
 *                                                     it, a space at the end: [1-2] reads "1-2], pos 5"
 *   garbage after valid input document                these two carry NO position
 *   more than one document in the input
 * </pre>
 *
 * <p>THE POSITION IS 1-BASED and falls in one of two places. A fault about a TOKEN that was read
 * reports the index just PAST it — a five-character keyword is pos 6, and a document that simply ran
 * out reports the index past its last character. A fault about a CHARACTER that was not read reports
 * that character's own index. A duplicate key reports its CLOSING QUOTE, which is the same rule read
 * the first way: just past the key's content.
 *
 * <p>WHAT COMES AFTER a complete document decides between the last two sentences: another object,
 * array or string is "more than one document", and anything else is "garbage". A document that starts
 * with a comma is garbage too — an empty prefix is a valid (null) document, so the comma is already
 * after one.
 */
final class JsonFaultReader {

    /** The greatest hex integer the reader converts: 2^127 - 1. */
    private static final BigInteger LARGEST_HEX_INTEGER = BigInteger.ONE.shiftLeft(127).subtract(BigInteger.ONE);

    private final String text;
    private int at;

    private JsonFaultReader(final String document) {
        this.text = document;
    }

    /**
     * The fault of a raw line break inside a string, which Snowflake's reader refuses where every other
     * control character is kept: a line feed or a carriage return, or a backslash before a carriage return
     * (a backslash before a line feed continues the string). The lenient reader accepts all three, so a
     * document it has read is asked this before its value stands.
     *
     * @param document the document as written
     * @return the fault, or null when no string holds a raw line break
     */
    static String lineBreakFault(final String document) {
        boolean inString = false;
        for (int i = 0; i < document.length(); i++) {
            final char c = document.charAt(i);
            if (!inString) {
                inString = c == '"';
                continue;
            }
            if (c == '\\') {
                if (i + 1 < document.length() && document.charAt(i + 1) == '\r') {
                    return badEscape(document, i);
                }
                i++;
            } else if (c == '"') {
                inString = false;
            } else if (c == '\n' || c == '\r') {
                return unterminated(document, i);
            }
        }
        return null;
    }

    /** A raw line break at {@code at}: a line feed has begun the next line, a carriage return is a column. */
    private static String unterminated(final String text, final int at) {
        if (text.charAt(at) == '\n') {
            int line = 1;
            for (int i = 0; i < at; i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            return "unterminated string, line " + (line + 1) + ", pos 0";
        }
        return "unterminated string, " + positionOf(text, at);
    }

    /** A backslash at {@code at} escaping a carriage return, reported just past the pair. */
    private static String badEscape(final String text, final int at) {
        return "bad escape sequence in the string, " + positionOf(text, at + 2);
    }

    /** The fault Snowflake names in {@code document}, or null when this reader finds none. */
    static String faultOf(final String document) {
        final JsonFaultReader reader = new JsonFaultReader(document);
        reader.skipWhitespace();
        if (reader.atEnd()) {
            return null;
        }
        if (reader.text.charAt(reader.at) == ',') {
            return "garbage after valid input document";
        }
        final String fault = reader.value();
        if (fault != null) {
            return fault;
        }
        reader.skipWhitespace();
        if (reader.atEnd()) {
            return null;
        }
        final char next = reader.text.charAt(reader.at);
        return next == '{' || next == '[' || next == '"'
            ? "more than one document in the input"
            : "garbage after valid input document";
    }

    /** One value, leaving the cursor past it. */
    private String value() {
        skipWhitespace();
        if (atEnd()) {
            return null;
        }
        final char c = text.charAt(at);
        if (c == '{') {
            return object();
        }
        if (c == '[') {
            return array();
        }
        if (c == '"') {
            return string();
        }
        if (c == '}' || c == ']') {
            return misplaced(c);
        }
        if (c == ',') {
            return "misplaced comma, " + positionOf(text, at);
        }
        if (c == '-' || c == '+' || c == '.' || c >= '0' && c <= '9') {
            return number();
        }
        if (isWordCharacter(c)) {
            return keyword();
        }
        return invalidCharacter(c);
    }

    private String object() {
        at++;
        final Set<String> keys = new HashSet<String>();
        skipWhitespace();
        if (atEnd()) {
            return incomplete("object");
        }
        if (text.charAt(at) == '}') {
            at++;
            return null;
        }
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return incomplete("object");
            }
            final char start = text.charAt(at);
            if (start == '}' || start == ']') {
                return misplaced(start);
            }
            if (start == ',') {
                return "misplaced comma, " + positionOf(text, at);
            }
            final int keyStart = at;
            if (start == '"') {
                final String unfinished = string();
                if (unfinished != null) {
                    return unfinished;
                }
            } else if (isDigit(start)) {
                // ★ A NUMBER IS NOT A NAME, and live says so in its own sentence rather than through
                // the character rule — {@code {1:1}} and {@code {1a:1}} both report it, at the digit.
                return "object attribute name cannot be a number, " + positionOf(text, at);
            } else if (isWordCharacter(start)) {
                // An UNQUOTED key is legal, so the keyword check that a value position would apply
                // must not run here: {a:1} is an object with one attribute.
                while (!atEnd() && isWordCharacter(text.charAt(at))) {
                    at++;
                }
            } else {
                return invalidCharacter(start);
            }
            final String key = start == '"'
                ? text.substring(keyStart + 1, at - 1) : text.substring(keyStart, at);
            if (!keys.add(key)) {
                return "duplicate object attribute \"" + key + "\", " + positionOf(text, at - 1);
            }
            final String separated = afterKey();
            if (separated != null) {
                return separated;
            }
            final String member = value();
            if (member != null) {
                return member;
            }
            skipWhitespace();
            if (atEnd()) {
                return incomplete("object");
            }
            final char after = text.charAt(at);
            if (after == '}') {
                at++;
                return null;
            }
            if (after == ']') {
                return misplaced(after);
            }
            if (after != ',') {
                return "missing comma, " + positionOf(text, at);
            }
            at++;
            skipWhitespace();
            if (atEnd()) {
                return incomplete("object");
            }
            if (text.charAt(at) == ',') {
                return "misplaced comma, " + positionOf(text, at);
            }
            if (text.charAt(at) == '}') {
                at++;
                return null;
            }
        }
    }

    /** The colon between a key and its value, or the fault written in its place. */
    private String afterKey() {
        skipWhitespace();
        if (atEnd()) {
            return incomplete("object");
        }
        final char c = text.charAt(at);
        if (c == '}' || c == ']' || c == '[') {
            return misplaced(c);
        }
        if (c == ',') {
            return "misplaced comma, " + positionOf(text, at);
        }
        if (c != ':') {
            // ★ TWO CLASSES, and the split is "could this character begin a VALUE here". A sign or a
            // point could start a number, so the name simply ended and the colon is what is missing;
            // anything else is legal nowhere outside a string and is reported where it stands. The
            // whitespace case reads as the first kind because the skip above has already passed it,
            // so the report lands on the next real character rather than on the space.
            return startsAValue(c) ? "missing colon, " + positionOf(text, at)
                : invalidCharacter(c);
        }
        at++;
        skipWhitespace();
        if (atEnd()) {
            return incomplete("object");
        }
        return text.charAt(at) == '}' ? misplaced('}') : null;
    }

    private String array() {
        at++;
        skipWhitespace();
        if (atEnd()) {
            return incomplete("array");
        }
        if (text.charAt(at) == ']') {
            at++;
            return null;
        }
        while (true) {
            skipWhitespace();
            if (atEnd()) {
                return incomplete("array");
            }
            if (text.charAt(at) == '}') {
                return misplaced('}');
            }
            if (text.charAt(at) == ']') {
                at++;
                return null;
            }
            if (text.charAt(at) != ',') {
                // A comma straight away is an element live reads as `undefined`, not a fault.
                final String element = value();
                if (element != null) {
                    return element;
                }
            }
            skipWhitespace();
            if (atEnd()) {
                return incomplete("array");
            }
            final char after = text.charAt(at);
            if (after == ']') {
                at++;
                return null;
            }
            if (after == '}') {
                return misplaced(after);
            }
            if (after != ',') {
                return "missing comma, " + positionOf(text, at);
            }
            at++;
        }
    }

    private String string() {
        at++;
        while (!atEnd()) {
            final char c = text.charAt(at);
            if (c == '\\') {
                if (at + 1 < text.length() && text.charAt(at + 1) == '\r') {
                    return badEscape(text, at);
                }
                at += 2;
                continue;
            }
            if (c == '\n' || c == '\r') {
                return unterminated(text, at);
            }
            at++;
            if (c == '"') {
                return null;
            }
        }
        return "unfinished string, " + positionOf(text, at);
    }

    private String number() {
        final int start = at;
        final char sign = text.charAt(at);
        if (sign == '-' || sign == '+') {
            at++;
            if (atEnd()) {
                return "no number after a sign, " + positionOf(text, at);
            }
            final char next = text.charAt(at);
            if (next == '-' || next == '+') {
                return "stray minus sign, " + positionOf(text, at);
            }
            if (!isDigit(next) && next != '.') {
                at = start;
                return keyword();
            }
        }
        final String hex = hexNumber(start);
        if (hex != null) {
            return hex.isEmpty() ? null : hex;
        }
        skipDigits();
        if (!atEnd() && text.charAt(at) == '.') {
            at++;
            skipDigits();
        }
        if (!atEnd() && (text.charAt(at) == 'e' || text.charAt(at) == 'E')) {
            at++;
            if (!atEnd() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                at++;
            }
            if (atEnd() || !isDigit(text.charAt(at))) {
                return "missing decimal exponent digits: '" + text.substring(start, at)
                    + "', " + positionOf(text, at);
            }
            skipDigits();
        }
        if (!atEnd() && continuesANumber(text.charAt(at))) {
            while (!atEnd() && continuesANumber(text.charAt(at))) {
                at++;
            }
            return "garbage in the numeric literal: " + text.substring(start, at)
                + (atEnd() ? " " : String.valueOf(text.charAt(at))) + ", " + positionOf(text, at);
        }
        return null;
    }

    /**
     * A HEXADECIMAL number, which the reader converts where it can: {@code 0x} with no digits and an
     * integer past the signed 128-bit range are its own sentences, an exponent with no digits is the
     * hexadecimal twin of the decimal one, and a token running into other characters is garbage like any
     * other literal. Null when no hex token stands here, and the empty string when one stands and reads
     * (live-verified).
     *
     * @param start where the literal began, a leading sign included
     */
    private String hexNumber(final int start) {
        final int digitsAt = text.charAt(start) == '+' ? start + 1 : start;
        if (digitsAt + 1 >= text.length() || text.charAt(digitsAt) != '0'
                || text.charAt(digitsAt + 1) != 'x' && text.charAt(digitsAt + 1) != 'X') {
            return null;
        }
        at = digitsAt + 2;
        final int integerAt = at;
        while (!atEnd() && isHexDigit(text.charAt(at))) {
            at++;
        }
        final int integerEnd = at;
        boolean floating = false;
        if (!atEnd() && text.charAt(at) == '.') {
            floating = true;
            at++;
            while (!atEnd() && isHexDigit(text.charAt(at))) {
                at++;
            }
        }
        if (!atEnd() && (text.charAt(at) == 'p' || text.charAt(at) == 'P')) {
            floating = true;
            at++;
            if (!atEnd() && (text.charAt(at) == '+' || text.charAt(at) == '-')) {
                at++;
            }
            if (atEnd() || !isDigit(text.charAt(at))) {
                return "missing hexadecimal exponent digits: '" + text.substring(start, at)
                    + "', " + positionOf(text, at);
            }
            skipDigits();
        }
        if (!atEnd() && continuesANumber(text.charAt(at))) {
            while (!atEnd() && continuesANumber(text.charAt(at))) {
                at++;
            }
            return "garbage in the numeric literal: " + text.substring(start, at)
                + (atEnd() ? " " : String.valueOf(text.charAt(at))) + ", " + positionOf(text, at);
        }
        if (!floating && integerEnd == integerAt) {
            return "hexadecimal integer number conversion error: " + text.substring(start, at)
                + ", " + positionOf(text, at);
        }
        if (!floating && new BigInteger(text.substring(integerAt, integerEnd), 16)
                .compareTo(LARGEST_HEX_INTEGER) > 0) {
            return "hexadecimal integer number conversion error: " + text.substring(start, at)
                + ", " + positionOf(text, at);
        }
        return "";
    }

    private static boolean isHexDigit(final char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    /** A bare word: one of the four JSON keywords, or a fault naming it. */
    private String keyword() {
        final int start = at;
        // ★ A VALUE-position word keeps a LEADING SIGN — {@code -a} is one word, reported whole —
        // where a NAME may not contain one at all. The two positions read different character sets,
        // which is why the name's predicate is not reused here.
        if (!atEnd() && (text.charAt(at) == '-' || text.charAt(at) == '+')) {
            at++;
        }
        while (!atEnd() && isWordCharacter(text.charAt(at))) {
            at++;
        }
        final String word = text.substring(start, at);
        if (word.equals("true") || word.equals("false") || word.equals("null")
                || word.equals("undefined")) {
            return null;
        }
        return "unknown keyword \"" + word + "\", " + positionOf(text, at);
    }

    private String incomplete(final String container) {
        return "incomplete " + container + " value, " + positionOf(text, at);
    }

    /** Whether a character carries a numeric literal on: a letter, a digit, a point or a sign. */
    private static boolean continuesANumber(final char c) {
        return isWordCharacter(c) || c == '.' || c == '-' || c == '+';
    }

    /** Whether a character could begin a JSON VALUE here, which is what chooses the sentence. */
    private static boolean startsAValue(final char c) {
        return c == '-' || c == '+' || c == '.' || isDigit(c) || isWordCharacter(c) || c == '"';
    }

    /**
     * The invalid-character sentence, naming a NON-ASCII character by its CODE POINT.
     *
     * <p>Live prints {@code U+00E4} rather than the letter itself — measured, and the only place in
     * this reader's vocabulary where a character is not shown as written.
     */
    private String invalidCharacter(final char c) {
        // ★ The CODE POINT form carries no quotes, where a character shown as written does — so the
        // quoting is part of the ASCII spelling and not of the sentence.
        final String shown;
        if (c >= 0x80) {
            shown = String.format(Locale.ROOT, "U+%04X", (int) c);
            return "invalid character outside of a string: " + shown + ", "
                + positionOf(text, at, utf8Length(c) - 1);
        } else if (c == '\\') {
            // The one character the message ESCAPES rather than showing as written (live-measured).
            shown = "\\\\";
        } else {
            shown = String.valueOf(c);
        }
        // ★ Reported at the character's LAST byte. For ASCII that is simply where it stands, but a
        // multi-byte one is reported at its END: measured, U+00E4 at byte 2 reads pos 3 and U+20AC
        // at byte 2 reads pos 4 — the width of the character, not its start, decides the number.
        return "invalid character outside of a string: '" + shown + "', "
            + positionOf(text, at, utf8Length(c) - 1);
    }

    /** How many bytes a character occupies once the document is UTF-8, which is how live counts. */
    private static int utf8Length(final char c) {
        if (c < 0x80) {
            return 1;
        }
        return c < 0x800 ? 2 : 3;
    }

    private String misplaced(final char closer) {
        return "misplaced " + closer + ", " + positionOf(text, at);
    }

    private void skipWhitespace() {
        while (!atEnd() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private void skipDigits() {
        while (!atEnd() && isDigit(text.charAt(at))) {
            at++;
        }
    }

    private boolean atEnd() {
        return at >= text.length();
    }

    private static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * The characters an UNQUOTED name is made of — ASCII letters, digits and the underscore, and
     * nothing else. {@code -} and {@code +} are deliberately absent: they end a name, and what
     * follows is the missing-colon report rather than more name. A NON-ASCII letter is absent for the
     * same reason, and live names it by CODE POINT when it reports it.
     */
    private static boolean isWordCharacter(final char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || isDigit(c) || c == '_';
    }

    /**
     * Where a fault stands, as Snowflake words it: a 1-based column WITHIN ITS LINE, with a {@code line N,}
     * clause only once past the first line. The offset is into the ORIGINAL document, leading whitespace and
     * all — live counts what was written, so {@code PARSE_JSON('  cdefg')} is pos 8 where the same document
     * without the spaces is pos 6.
     */
    private static String positionOf(final String text, final int at) {
        return positionOf(text, at, 0);
    }

    /**
     * @param extraBytes bytes of the offending character itself to count, so a multi-byte one is
     *                   reported at its LAST byte rather than its first
     */
    private static String positionOf(final String text, final int at, final int extraBytes) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < at && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        // The column counts BYTES, not characters — every ASCII document reads the same either way,
        // and a document carrying a multi-byte character does not.
        int bytes = 0;
        for (int i = lineStart; i < at && i < text.length(); i++) {
            bytes += utf8Length(text.charAt(i));
        }
        final String column = "pos " + (bytes + 1 + extraBytes);
        return line > 1 ? "line " + line + ", " + column : column;
    }
}
