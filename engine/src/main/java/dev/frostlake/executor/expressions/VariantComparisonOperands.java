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

package dev.frostlake.executor.expressions;

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;

/**
 * How a comparison between a VARIANT and a value of another type converts its operands on the row. Live
 * converts ONE side, chosen by the other side's type and, for three families, by which side the VARIANT is
 * written on (all live-verified over a VARIANT holding 1 and one holding a string):
 *
 * <pre>
 *   the typed side                VARIANT on the left         VARIANT on the right
 *   DATE, TIME, a timestamp       the VARIANT is cast to it   the VARIANT is cast to it
 *   BOOLEAN, ARRAY, OBJECT        the typed side is read      the VARIANT is cast to it
 *                                 as a VARIANT
 *   a number                      the number is read as a VARIANT, either way round
 *   a text                        neither: the VARIANT is compared as its text
 * </pre>
 *
 * <p>So {@code va = d} and {@code d = va} both fail "Failed to cast variant value 1 to DATE", {@code b = va}
 * fails the cast to BOOLEAN where {@code va = b} is FALSE and {@code va > b} TRUE (a VARIANT number sorts
 * after a VARIANT boolean), {@code ar = va} is TRUE because 1 casts to [1], and a VARIANT holding the string
 * '1' equals no number. The cast is the one {@code ::} performs, a JSON null reading as SQL NULL.
 */
final class VariantComparisonOperands {

    private VariantComparisonOperands() {
    }

    /**
     * The type one operand of a comparison converts to before the comparison, or null when it stays as it is.
     *
     * @param own    the operand's static type
     * @param beside the other operand's static type
     * @param onLeft whether the operand is written on the left
     * @return the cast target's name — VARIANT for a typed operand read as a VARIANT — or null
     */
    static String conversionTarget(final DataType own, final DataType beside, final boolean onLeft) {
        if (own == null || beside == null) {
            return null;
        }
        final boolean ownVariant = own instanceof VariantType;
        if (ownVariant == beside instanceof VariantType) {
            return null;
        }
        if (ownVariant) {
            if (beside instanceof DateTimeType) {
                return beside.getName();
            }
            return onLeft ? null : containerTarget(beside);
        }
        if (own instanceof NumericType) {
            return "VARIANT";
        }
        return onLeft || containerTarget(own) == null ? null : "VARIANT";
    }

    /** The name of the family a VARIANT on the right is cast to — BOOLEAN, ARRAY or OBJECT — or null. */
    private static String containerTarget(final DataType type) {
        if (type instanceof BooleanType) {
            return "BOOLEAN";
        }
        if (type instanceof ArrayType) {
            return "ARRAY";
        }
        return type instanceof ObjectType ? "OBJECT" : null;
    }
}
