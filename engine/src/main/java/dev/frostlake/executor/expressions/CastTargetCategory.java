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

/**
 * The coarse category a CAST target type falls into, derived from its base type name with aliases collapsed
 * (e.g. INTEGER/INT/BIGINT → {@link #INTEGER}, NUMBER/DECIMAL/NUMERIC → {@link #DECIMAL}). Any precision/scale on
 * the raw type (e.g. {@code NUMBER(10,2)}) is applied separately, so this is only the dispatch category.
 */
enum CastTargetCategory {
    INTEGER, FLOAT, DECIMAL, STRING, BOOLEAN, BINARY, ARRAY, OBJECT, UUID;

    /** Classify a cast target's base type name (uppercased, without any precision/scale); null if unhandled. */
    static CastTargetCategory fromTypeName(final String baseType) {
        switch (baseType) {
            // Every integer alias rounds, not truncates — live, `2.7::SMALLINT`, `2.5::TINYINT`
            // and `2.5::BYTEINT` are all 3 and `-2.5::TINYINT` is -3, exactly like INT / INTEGER / BIGINT.
            // The three small spellings used to fall through to null here and passed the value straight
            // out, so `2.7::SMALLINT` stayed 2.7.
            case "INTEGER": case "INT": case "BIGINT":
            case "SMALLINT": case "TINYINT": case "BYTEINT": return INTEGER;
            case "FLOAT": case "FLOAT4": case "FLOAT8": case "DOUBLE": case "DOUBLEPRECISION": case "REAL": case "DECFLOAT": return FLOAT;
            case "NUMBER": case "DECIMAL": case "NUMERIC": return DECIMAL;
            case "VARCHAR": case "STRING": case "TEXT": return STRING;
            case "BOOLEAN": return BOOLEAN;
            case "BINARY": case "VARBINARY": return BINARY;
            case "ARRAY": return ARRAY;
            // VECTOR(FLOAT|INT, n) casts take the array value as-is; the dimension is declarative here.
            case "VECTOR": return ARRAY;
            case "OBJECT": return OBJECT;
            case "UUID": return UUID;
            default: return null;
        }
    }
}
