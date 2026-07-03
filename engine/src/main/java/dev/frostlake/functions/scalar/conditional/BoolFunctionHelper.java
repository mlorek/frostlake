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

package dev.frostlake.functions.scalar.conditional;

/** Shared truthiness helper for the BOOLAND / BOOLOR / BOOLNOT / BOOLXOR functions. */
public final class BoolFunctionHelper {

    private BoolFunctionHelper() {
    }

    /**
     * Interprets a value as a three-valued boolean. NULL stays NULL (UNKNOWN); booleans pass
     * through; numbers and numeric/boolean strings are TRUE when non-zero and FALSE when zero.
     * Non-numeric, non-boolean strings are treated as TRUE (present, non-null).
     */
    public static Boolean truthiness(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }
        final String s = value.toString().trim();
        if (s.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (s.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        try {
            return Double.parseDouble(s) != 0.0;
        } catch (final NumberFormatException e) {
            return Boolean.TRUE;
        }
    }
}
