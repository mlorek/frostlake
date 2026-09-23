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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;

/**
 * The static types a whole predicate may not have. A WHERE, a join condition, a HAVING or a QUALIFY whose own type
 * is text, a number, a semi-structured value (a VARIANT, an OBJECT, an ARRAY, a MAP, a path into one), a date or
 * time, a binary, a vector or a geospatial value is refused while compiling as {@code Invalid data type [T] for
 * predicate [...]}, over empty inputs too; only a BOOLEAN is a predicate. Inside AND, OR and NOT the same operand
 * is converted instead (live-verified).
 */
final class NonPredicateTypes {

    private NonPredicateTypes() {
    }

    /**
     * Whether {@code type}, the static type of a whole predicate, refuses it.
     *
     * @param type the inferred type, or null when undetermined
     * @return true for a type that is no predicate
     */
    static boolean refuses(final DataType type) {
        return type instanceof StringType || type instanceof NumericType
            || type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType
            || type instanceof MapType || type instanceof DateTimeType || type instanceof BinaryType
            || type instanceof VectorType || type instanceof GeographyType || type instanceof GeometryType;
    }
}
