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

import java.util.ArrayList;
import java.util.List;

/** Reads the text of an {@link ExtractionPath}, one step after another. */
final class ExtractionPathReader {

    private final String text;
    private final List<ExtractionPathStep> steps = new ArrayList<>();
    private int pos;

    ExtractionPathReader(final String text) {
        this.text = text;
    }

    /**
     * Reads the whole text.
     *
     * @return the path, or null when the text is malformed
     */
    ExtractionPath read() {
        if (text.isEmpty()) {
            return null;
        }
        final char first = text.charAt(0);
        boolean read;
        if (isSeparator(first)) {
            read = separated();
        } else if (first == '[') {
            read = bracket();
        } else if (first == '"') {
            read = quotedName();
        } else {
            read = bareName();
        }
        while (read) {
            skipBlanks();
            if (pos == text.length()) {
                return new ExtractionPath(steps);
            }
            final char c = text.charAt(pos);
            if (isSeparator(c)) {
                read = separated();
            } else if (c == '[') {
                read = bracket();
            } else {
                read = c == '"' && quotedName();
            }
        }
        return null;
    }

    /** A separator, at most one blank, and the name it announces. */
    private boolean separated() {
        pos++;
        if (pos < text.length() && isBlank(text.charAt(pos))) {
            pos++;
        }
        if (pos == text.length()) {
            return false;
        }
        return text.charAt(pos) == '"' ? quotedName() : bareName();
    }

    private boolean bareName() {
        final int start = pos;
        if (pos == text.length() || !isNameStart(text.charAt(pos))) {
            return false;
        }
        pos++;
        while (pos < text.length() && isNamePart(text.charAt(pos))) {
            pos++;
        }
        steps.add(ExtractionPathStep.ofKey(text.substring(start, pos)));
        return true;
    }

    private boolean quotedName() {
        final String name = quoted();
        if (name == null) {
            return false;
        }
        steps.add(ExtractionPathStep.ofKey(name));
        return true;
    }

    /** A bracket holding a quoted name, or digits after an optional minus sign. */
    private boolean bracket() {
        pos++;
        skipBlanks();
        if (pos == text.length()) {
            return false;
        }
        final char c = text.charAt(pos);
        if (c == '"' || c == '\'') {
            if (!quotedName()) {
                return false;
            }
        } else {
            final boolean negative = c == '-';
            if (negative) {
                pos++;
            }
            final int start = pos;
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                return false;
            }
            steps.add(ExtractionPathStep.ofPosition(position(text.substring(start, pos), negative)));
        }
        skipBlanks();
        if (pos == text.length() || text.charAt(pos) != ']') {
            return false;
        }
        pos++;
        return true;
    }

    /** The position some digits name: only zero keeps one behind a minus sign, and a count too long has none. */
    private static long position(final String digits, final boolean negative) {
        long value = 0;
        for (int i = 0; i < digits.length(); i++) {
            value = value * 10 + (digits.charAt(i) - '0');
            if (value > Integer.MAX_VALUE) {
                return ExtractionPathStep.NO_POSITION;
            }
        }
        return negative && value != 0 ? ExtractionPathStep.NO_POSITION : value;
    }

    /** The text between the quote at the cursor and the one that closes it, or null when none closes it. */
    private String quoted() {
        final int end = closingQuote(text, pos);
        if (end < 0) {
            return null;
        }
        final String name = unquoted(text, pos, end);
        pos = end + 1;
        return name;
    }

    private void skipBlanks() {
        while (pos < text.length() && isBlank(text.charAt(pos))) {
            pos++;
        }
    }

    /**
     * Where a quoted name ends.
     *
     * @param text the path
     * @param open the index of the opening quote
     * @return the index of the closing quote, or -1 when the text ends first
     */
    static int closingQuote(final String text, final int open) {
        final char quote = text.charAt(open);
        int at = open + 1;
        while (at < text.length()) {
            final char c = text.charAt(at);
            if (c == '\\') {
                at += 2;
            } else if (c != quote) {
                at++;
            } else if (at + 1 < text.length() && text.charAt(at + 1) == quote) {
                at += 2;
            } else {
                return at;
            }
        }
        return -1;
    }

    /** A quoted name's text: a backslash escapes as in JSON, and a doubled quote stands for one. */
    private static String unquoted(final String text, final int open, final int close) {
        final char quote = text.charAt(open);
        final StringBuilder name = new StringBuilder();
        int at = open + 1;
        while (at < close) {
            final char c = text.charAt(at);
            if (c == quote) {
                name.append(quote);
                at += 2;
            } else if (c != '\\') {
                name.append(c);
                at++;
            } else {
                at += 2;
                at += appendEscaped(name, text, at, close);
            }
        }
        return name.toString();
    }

    /**
     * Adds the character an escape stands for.
     *
     * @param name  the name so far
     * @param text  the path
     * @param after the index past the escaped character
     * @param close the index of the closing quote
     * @return how many further characters the escape took
     */
    private static int appendEscaped(final StringBuilder name, final String text, final int after, final int close) {
        final char escaped = text.charAt(after - 1);
        if (escaped == 'u') {
            final int code = after + 4 <= close ? hex(text, after) : -1;
            if (code >= 0) {
                name.append((char) code);
                return 4;
            }
        }
        switch (escaped) {
            case 'n':
                name.append('\n');
                return 0;
            case 't':
                name.append('\t');
                return 0;
            case 'r':
                name.append('\r');
                return 0;
            case 'b':
                name.append('\b');
                return 0;
            case 'f':
                name.append('\f');
                return 0;
            default:
                name.append(escaped);
                return 0;
        }
    }

    private static int hex(final String text, final int from) {
        int code = 0;
        for (int i = from; i < from + 4; i++) {
            final int digit = Character.digit(text.charAt(i), 16);
            if (digit < 0) {
                return -1;
            }
            code = code * 16 + digit;
        }
        return code;
    }

    static boolean isSeparator(final char c) {
        return c == '.' || c == ':';
    }

    static boolean isBlank(final char c) {
        return c == ' ' || c == '\t';
    }

    static boolean isDigit(final char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isNameStart(final char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '$' || c == '@' || c >= 0x80;
    }

    private static boolean isNamePart(final char c) {
        return isNameStart(c) || isDigit(c);
    }
}
