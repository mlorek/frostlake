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

import dev.frostlake.functions.WholeNumbers;

/** Shared truthiness helper for the BOOLAND / BOOLOR / BOOLNOT / BOOLXOR functions. */
public final class BoolFunctionHelper {

    private BoolFunctionHelper() {
    }

    /**
     * Interprets a value as a three-valued boolean, as the functions read their arguments: as NUMBERs, TRUE when
     * not zero (live-verified). NULL stays NULL (UNKNOWN), a BOOLEAN is itself, a FLOAT is true when not zero, NaN
     * included, and a text or a VARIANT converts as an argument read as a NUMBER converts (see
     * {@link WholeNumbers#decimalOf}): {@code BOOLAND('1.5', 1)} is TRUE, {@code BOOLAND('yes', 1)} is "Numeric
     * value 'yes' is not recognized" and a VARIANT holding "yes" "Failed to cast variant value "yes" to FIXED".
     */
    public static Boolean truthiness(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Double || value instanceof Float) {
            return ((Number) value).doubleValue() != 0.0;
        }
        return WholeNumbers.decimalOf(value).signum() != 0;
    }
}
