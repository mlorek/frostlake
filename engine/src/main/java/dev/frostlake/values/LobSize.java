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
 * StringResultWidths and BinaryType), so no function result can be wider. The sentence is Snowflake's LOB-size
 * error; its wording for a function result was not measured.
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
            throw new RuntimeException("Max LOB size (" + maximum + ") exceeded, actual size of parsed column is "
                + bytes);
        }
    }
}
