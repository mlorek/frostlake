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

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

/**
 * The compatibility classes of the scalar-UDF return-type check, exactly as a real account divides
 * them (live-verified): the fixed-point and floating-point numerics are SEPARATE families, the three
 * timestamp flavors are each their own family, and VARIANT / ARRAY / OBJECT match only themselves —
 * VARIANT does not absorb the other two. Length, precision and scale never participate.
 */
enum ReturnTypeFamily {
    FIXED,
    REAL,
    TEXT,
    BOOLEAN,
    DATE,
    TIME,
    TIMESTAMP_NTZ,
    TIMESTAMP_LTZ,
    TIMESTAMP_TZ,
    BINARY,
    VARIANT,
    ARRAY,
    OBJECT;

    /** The family of {@code type}, or null for the kinds the check does not judge. */
    static ReturnTypeFamily of(final DataType type) {
        if (type instanceof NumericType) {
            final String name = type.getName().toUpperCase();
            if (name.startsWith("FLOAT") || name.startsWith("DOUBLE") || name.equals("REAL")) {
                return REAL;
            }
            return FIXED;
        }
        if (type instanceof StringType) {
            return TEXT;
        }
        if (type instanceof BooleanType) {
            return BOOLEAN;
        }
        if (type instanceof DateTimeType) {
            switch (type.getName().toUpperCase()) {
                case "DATE":
                    return DATE;
                case "TIME":
                    return TIME;
                case "TIMESTAMP_LTZ":
                    return TIMESTAMP_LTZ;
                case "TIMESTAMP_TZ":
                    return TIMESTAMP_TZ;
                default:
                    // TIMESTAMP, DATETIME and TIMESTAMP_NTZ are one flavor.
                    return TIMESTAMP_NTZ;
            }
        }
        if (type instanceof BinaryType) {
            return BINARY;
        }
        if (type instanceof VariantType) {
            return VARIANT;
        }
        if (type instanceof ArrayType) {
            return ARRAY;
        }
        if (type instanceof ObjectType) {
            return OBJECT;
        }
        return null;
    }
}
