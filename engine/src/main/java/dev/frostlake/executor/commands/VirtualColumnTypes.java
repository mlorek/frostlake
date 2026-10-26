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

package dev.frostlake.executor.commands;

import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;

/**
 * Whether a virtual column's declared type takes the type of its expression (see {@link VirtualColumnDefinitions}):
 * an exact number of the same scale and no more digits, a text no longer than the column's, and any other type only
 * itself - a time or a timestamp of its own flavour and fractional precision. FLOAT, DECFLOAT and the exact numbers
 * are three types.
 */
final class VirtualColumnTypes {

    private VirtualColumnTypes() {
    }

    /**
     * @param declared the column's type
     * @param found    the expression's
     * @return whether the column takes the expression
     */
    static boolean matches(final DataType declared, final DataType found) {
        if (exact(declared) && exact(found)) {
            final NumericType column = (NumericType) declared;
            final NumericType value = (NumericType) found;
            return value.getScale() == column.getScale() && value.getPrecision() <= column.getPrecision();
        }
        if (declared instanceof StringType && found instanceof StringType) {
            return ((StringType) found).getMaxLength() <= ((StringType) declared).getMaxLength();
        }
        return SqlTypeNames.canonical(declared).equals(SqlTypeNames.canonical(found));
    }

    private static boolean exact(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type) && !NumericType.isDecfloat(type);
    }
}
