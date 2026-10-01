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

package dev.frostlake.values;

import java.nio.charset.StandardCharsets;

/**
 * Text measured and cut by CHARACTER — a Unicode code point — which is the unit every Snowflake string
 * function counts in. A Java {@code String} indexes UTF-16 units instead, so a character outside the Basic
 * Multilingual Plane (an emoji, a mathematical letter) is two of them: counted that way {@code LENGTH('😀')}
 * would read 2, and a cut after its first unit would hand back half a character — a lone surrogate no
 * client can encode. Live-verified:
 * the account counts that character once in LENGTH, SUBSTR, LEFT, RIGHT, CHARINDEX, POSITION, LPAD, RPAD,
 * INSERT, TRANSLATE, EDITDISTANCE and the REGEXP positions, in a literal's declared width, and against a
 * declared {@code VARCHAR(n)} — {@code '😀😀'} fits a {@code VARCHAR(2)}.
 *
 * <p>Every index here is 0-based and counted in characters; every out-of-range index is clamped to the
 * text, so a caller that has already applied its own window rule never meets an exception.
 */
public final class CodePointText {

    private CodePointText() {
    }

    /**
     * The number of characters in a text.
     *
     * @param text the text
     * @return its length in characters
     */
    public static int length(final String text) {
        return text.codePointCount(0, text.length());
    }

    /**
     * The UTF-16 offset at which a character index begins, clamped to {@code [0, text.length()]}.
     *
     * @param text       the text
     * @param characters the 0-based character index
     * @return the matching UTF-16 offset
     */
    public static int offset(final String text, final int characters) {
        if (characters <= 0) {
            return 0;
        }
        if (characters >= length(text)) {
            return text.length();
        }
        return text.offsetByCodePoints(0, characters);
    }

    /**
     * The character index at which a UTF-16 offset falls.
     *
     * @param text       the text
     * @param unitOffset a UTF-16 offset, clamped to the text
     * @return the 0-based character index
     */
    public static int characterIndex(final String text, final int unitOffset) {
        return text.codePointCount(0, Math.min(Math.max(unitOffset, 0), text.length()));
    }

    /**
     * The characters {@code [from, to)} of a text.
     *
     * @param text the text
     * @param from the first character's 0-based index
     * @param to   the index just past the last character
     * @return the slice, empty when the window holds no character
     */
    public static String slice(final String text, final int from, final int to) {
        final int start = offset(text, from);
        return text.substring(start, Math.max(start, offset(text, to)));
    }

    /**
     * Where a needle first occurs at or after a character index.
     *
     * @param haystack      the text searched
     * @param needle        the text sought
     * @param fromCharacter the 0-based character index the search starts at
     * @return the 0-based character index of the match, or -1 when there is none
     */
    public static int indexOf(final String haystack, final String needle, final int fromCharacter) {
        final int found = haystack.indexOf(needle, offset(haystack, fromCharacter));
        return found < 0 ? -1 : characterIndex(haystack, found);
    }

    /**
     * Exactly {@code characters} characters of a pattern repeated from its start — the pad LPAD and RPAD
     * lay down.
     *
     * @param pattern    the pattern, not empty
     * @param characters how many characters to produce
     * @return the repeated pattern, cut to that many characters
     */
    public static String repeatTo(final String pattern, final int characters) {
        final StringBuilder out = new StringBuilder();
        int at = 0;
        for (int produced = 0; produced < characters; produced++) {
            final int point = pattern.codePointAt(at);
            out.appendCodePoint(point);
            at += Character.charCount(point);
            if (at >= pattern.length()) {
                at = 0;
            }
        }
        return out.toString();
    }

    /**
     * The code points of a text, in order.
     *
     * @param text the text
     * @return one element per character
     */
    public static int[] codePoints(final String text) {
        final int[] points = new int[length(text)];
        int at = 0;
        for (int i = 0; i < points.length; i++) {
            final int point = text.codePointAt(at);
            points[i] = point;
            at += Character.charCount(point);
        }
        return points;
    }

    /**
     * Where a text's leading run of trimmed characters ends: every character in {@code set}, or whitespace
     * when the set is null. Compared by character, so a supplementary one is never split — '😁' shares its
     * first UTF-16 unit with '😀', and {@code TRIM('😁a', '😀')} keeps it whole (live-verified).
     *
     * @param text the text
     * @param set  the characters trimmed, or null for whitespace
     * @return the UTF-16 offset the kept text starts at
     */
    public static int trimmedStart(final String text, final String set) {
        int at = 0;
        while (at < text.length() && inTrimSet(text.codePointAt(at), set)) {
            at += Character.charCount(text.codePointAt(at));
        }
        return at;
    }

    /**
     * Where a text's kept part ends once its trailing run of trimmed characters is cut — the mirror of
     * {@link #trimmedStart}.
     *
     * @param text  the text
     * @param set   the characters trimmed, or null for whitespace
     * @param floor the UTF-16 offset the cut never passes
     * @return the UTF-16 offset the kept text ends at
     */
    public static int trimmedEnd(final String text, final String set, final int floor) {
        int at = text.length();
        while (at > floor && inTrimSet(text.codePointBefore(at), set)) {
            at -= Character.charCount(text.codePointBefore(at));
        }
        return at;
    }

    private static boolean inTrimSet(final int point, final String set) {
        return set == null ? Character.isWhitespace(point) : set.indexOf(point) >= 0;
    }

    /**
     * The first byte of a text's UTF-8 encoding, which is what ASCII answers: {@code ASCII('é')} is 195 and
     * {@code ASCII('😀')} 240, the lead bytes of their encodings, where a code unit would read 233 and 55357
     * (live-verified). An empty text answers 0.
     *
     * @param text the text
     * @return the lead byte as an unsigned value
     */
    public static long leadByte(final String text) {
        if (text.isEmpty()) {
            return 0L;
        }
        final int end = Character.charCount(text.codePointAt(0));
        return text.substring(0, end).getBytes(StandardCharsets.UTF_8)[0] & 0xFF;
    }
}
