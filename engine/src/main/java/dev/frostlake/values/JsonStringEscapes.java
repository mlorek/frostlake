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

import tools.jackson.core.SerializableString;
import tools.jackson.core.io.CharacterEscapes;

/**
 * The characters a semi-structured value's JSON text escapes inside a string, as the account prints them
 * (live-verified): every control character below U+0020 — the five with a short form keep it
 * ({@code \b}, {@code \t}, {@code \n}, {@code \f}, {@code \r}), the rest take the six-character
 * &#92;u00XX form — and DEL, U+007F, which a standard writer leaves raw. The hexadecimal digits are LOWER
 * case (&#92;u000b, &#92;u007f); that is the writer's feature, not this table's. Nothing above U+007F is
 * escaped, the C1 controls, U+2028 and a byte-order mark included.
 */
public final class JsonStringEscapes extends CharacterEscapes {

    private static final long serialVersionUID = 1L;

    /** DEL, the one character from U+0020 to U+007F the account escapes. */
    private static final int DELETE = 0x7F;

    private final int[] asciiEscapes;

    /** The standard JSON escapes with DEL added. */
    public JsonStringEscapes() {
        asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
        asciiEscapes[DELETE] = CharacterEscapes.ESCAPE_STANDARD;
    }

    @Override
    public int[] getEscapeCodesForAscii() {
        return asciiEscapes;
    }

    @Override
    public SerializableString getEscapeSequence(final int ch) {
        return null;
    }
}
