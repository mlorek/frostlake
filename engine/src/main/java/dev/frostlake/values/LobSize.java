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

/**
 * The largest value a text or a binary can be, and the refusal a function that would build a larger one earns
 * BEFORE it allocates anything. A VARCHAR holds at most 128 MB and a BINARY 64 MB: the account declares a text
 * nothing bounds as VARCHAR(134217728) and an unsized binary as BINARY(67108864) (live-verified, see
 * StringResultWidths and BinaryType), so no function result can be wider. REPEAT, LPAD, RPAD and SPACE refuse a
 * longer one, text or binary, as {@code String '(padded string)' is too long and would be truncated}
 * (live-verified).
 *
 * <p>REPEAT, LPAD, RPAD and SPACE used to build whatever their count asked for: a count of 10^10 was held to
 * Integer.MAX_VALUE and the statement died of {@code OutOfMemoryError} rather than of an SQL error, taking the
 * heap every other session shares with it.
 */
public final class LobSize {

    /** The most bytes a text value holds, 128 MB. */
    public static final long MAX_TEXT_BYTES = 134_217_728L;

    /** The most bytes a binary value holds, 64 MB. */
    public static final long MAX_BINARY_BYTES = 67_108_864L;

    /** The most characters a refusal shows of a value too long for its place. */
    private static final int SHOWN = 200;

    private LobSize() {
    }

    /**
     * Refuse a text of {@code bytes} UTF-8 bytes when it is past {@link #MAX_TEXT_BYTES}.
     *
     * @param bytes the size the value would have, saturated at Long.MAX_VALUE
     */
    public static void requireText(final long bytes) {
        require(bytes, MAX_TEXT_BYTES);
    }

    /**
     * Refuse a binary of {@code bytes} bytes when it is past {@link #MAX_BINARY_BYTES}.
     *
     * @param bytes the size the value would have
     */
    public static void requireBinary(final long bytes) {
        require(bytes, MAX_BINARY_BYTES);
    }

    private static void require(final long bytes, final long maximum) {
        if (bytes > maximum) {
            throw new RuntimeException("String '(padded string)' is too long and would be truncated");
        }
    }

    /**
     * A value as a refusal of it as too long shows it: whole up to 200 characters, its first 200 and {@code ...} past
     * them, {@code String 'abbb…bbb...' is too long and would be truncated} (live-verified).
     *
     * @param value the value
     * @return what the refusal shows of it
     */
    public static String shown(final String value) {
        if (value.length() <= SHOWN || value.codePointCount(0, value.length()) <= SHOWN) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, SHOWN)) + "...";
    }

    /**
     * Refuse a text longer than a text can be, as the concatenation that made it: {@code TO_JSON(a) || 'x'} over an
     * array whose text passes the size is {@code String '[1,2,...' is too long and would be truncated in 'CONCAT'}
     * (live-verified).
     *
     * @param text the concatenation
     * @return the text, when it fits
     */
    public static String requireConcatenated(final String text) {
        if (text.length() > MAX_TEXT_BYTES && text.codePointCount(0, text.length()) > MAX_TEXT_BYTES) {
            throw tooLongIn(text, "CONCAT");
        }
        return text;
    }

    /**
     * The refusal of a text a function builds past what a text can be, showing the text and naming the function the
     * account builds it with: INSERT's result is {@code String 'bbb…...' is too long and would be truncated in
     * 'CONCAT'}, and HEX_ENCODE shows its argument, {@code ... in 'HEX_ENCODE'} (live-verified).
     *
     * @param text     the text shown, or as much of its beginning as is past 200 characters
     * @param function the function named
     * @return the refusal
     */
    public static RuntimeException tooLongIn(final String text, final String function) {
        return new RuntimeException("String '" + shown(text) + "' is too long and would be truncated in '" + function
            + "'");
    }

    /**
     * The refusal of a result too long to hold that the account names rather than shows: {@code String '(REPLACE
     * result)' is too long and would be truncated}, REGEXP_REPLACE's {@code (regexp_replace result)} and LISTAGG's
     * {@code (LISTAGG result)} (live-verified).
     *
     * @param named what the refusal names
     * @return the refusal
     */
    public static RuntimeException tooLongNamed(final String named) {
        return new RuntimeException("String '(" + named + ")' is too long and would be truncated");
    }

    /**
     * Refuse a text a semi-structured value converts to that is longer than a text can be: {@code
     * ARRAY_INSERT([1, 2], 14000000, 9)::VARCHAR} is {@code String '[1,2,undefined,...' is too long and would be
     * truncated}, where a text that fits, at thirteen million holes, converts whole (live-verified).
     *
     * @param text the converted text
     * @return the text, when it fits
     */
    public static String requireConverted(final String text) {
        if (text.length() > MAX_TEXT_BYTES && text.codePointCount(0, text.length()) > MAX_TEXT_BYTES) {
            throw tooLong(text);
        }
        return text;
    }

    /**
     * Refuse a text a function builds once it would hold more characters than a text can, showing its beginning: a
     * REPLACE or REGEXP_REPLACE whose replacements multiply its subject's length is refused before the whole of it is
     * held, as {@code String '<its first 200 characters>...' is too long and would be truncated}.
     *
     * @param beginning  the text's beginning, at least 201 characters of it where it has them
     * @param characters how many characters the whole text would hold
     */
    public static void requireBuilt(final CharSequence beginning, final long characters) {
        if (characters > MAX_TEXT_BYTES) {
            throw tooLong(beginning.toString());
        }
    }

    /**
     * How many characters of a text's beginning a refusal of it needs: twice what it shows, so the beginning still
     * holds 200 characters however many of them are surrogate pairs.
     *
     * @return the length of the beginning to keep
     */
    public static int shownBeginning() {
        return 2 * SHOWN + 2;
    }

    /**
     * The refusal of a text too long for its place, showing as much of it as the account shows.
     *
     * @param text the text, or as much of its beginning as is past 200 characters
     * @return the refusal
     */
    public static RuntimeException tooLong(final String text) {
        return new RuntimeException("String '" + shown(text) + "' is too long and would be truncated");
    }
}
