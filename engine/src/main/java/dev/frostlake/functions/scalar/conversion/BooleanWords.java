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

package dev.frostlake.functions.scalar.conversion;

import java.util.Locale;

/**
 * The words a text spells a BOOLEAN with: {@code true}, {@code t}, {@code yes}, {@code y}, {@code on} and
 * {@code 1}, and {@code false}, {@code f}, {@code no}, {@code n}, {@code off} and {@code 0} — in any case,
 * with blanks about them allowed. Nothing else is one: not a prefix ({@code tr}), not another number
 * ({@code 2}, {@code 1.0}, {@code -1}), not the empty text (live-verified).
 */
public final class BooleanWords {

    private BooleanWords() {
    }

    /**
     * The BOOLEAN a text spells.
     *
     * @param text the text to read
     * @return the value, or null when the text spells none
     */
    public static Boolean read(final String text) {
        switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "TRUE": case "T": case "YES": case "Y": case "ON": case "1":
                return Boolean.TRUE;
            case "FALSE": case "F": case "NO": case "N": case "OFF": case "0":
                return Boolean.FALSE;
            default:
                return null;
        }
    }
}
