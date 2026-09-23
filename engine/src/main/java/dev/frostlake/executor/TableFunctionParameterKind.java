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

package dev.frostlake.executor;

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.types.VectorType;

/**
 * What a table function's parameter will take. Snowflake type-checks these at COMPILE time and names
 * the offending type in the refusal — {@code FLATTEN(INPUT =&gt; '[1,2,3]')} is
 * {@code invalid type [VARCHAR(7)] for parameter 'INPUT'}, not a silent coercion of the JSON text.
 *
 * <p>Each kind lists only the families measured to be REFUSED. A type this enum does not recognise is
 * accepted, so an expression whose type cannot be determined statically can never be rejected here.
 */
public enum TableFunctionParameterKind {

    /**
     * VARIANT, OBJECT or ARRAY. Live refuses VARCHAR, NUMBER, BOOLEAN and DATE alike, so a JSON string
     * has to be parsed ({@code PARSE_JSON}) or cast before FLATTEN will look at it. A VECTOR is no ARRAY
     * either: {@code FLATTEN([1,2,3]::VECTOR(FLOAT,3))} is
     * {@code invalid type [VECTOR(FLOAT, 3)] for parameter '1'} (live-verified).
     */
    SEMI_STRUCTURED {
        @Override
        boolean accepts(final DataType declared) {
            return !(declared instanceof StringType || declared instanceof NumericType
                || declared instanceof BooleanType || declared instanceof DateTimeType
                || declared instanceof BinaryType || declared instanceof VectorType);
        }
    },

    /** VARCHAR — {@code FLATTEN(…, PATH =&gt; 1)} is {@code invalid type [NUMBER(1,0)] for parameter 'PATH'}. */
    STRING {
        @Override
        boolean accepts(final DataType declared) {
            return !(declared instanceof NumericType || declared instanceof BooleanType
                || declared instanceof DateTimeType || declared instanceof BinaryType
                || declared instanceof VariantType || declared instanceof ArrayType
                || declared instanceof ObjectType);
        }
    },

    /**
     * TIMESTAMP. The history functions' leading positional parameters are time-range bounds, so
     * {@code QUERY_HISTORY(2)} is {@code invalid type [NUMBER(1,0)] for parameter
     * 'END_TIME_RANGE_START'} — and so is a date-shaped STRING, which is not implicitly converted.
     */
    TIMESTAMP {
        @Override
        boolean accepts(final DataType declared) {
            return !(declared instanceof StringType || declared instanceof NumericType
                || declared instanceof BooleanType || declared instanceof BinaryType
                || declared instanceof VariantType || declared instanceof ArrayType
                || declared instanceof ObjectType);
        }
    },

    /**
     * BOOLEAN. The string {@code 'TRUE'} does NOT count: live answers
     * {@code invalid type [VARCHAR(4)] for parameter 'OUTER'}, so the flags take a boolean literal.
     */
    BOOLEAN {
        @Override
        boolean accepts(final DataType declared) {
            return !(declared instanceof StringType || declared instanceof NumericType
                || declared instanceof DateTimeType || declared instanceof BinaryType
                || declared instanceof VariantType || declared instanceof ArrayType
                || declared instanceof ObjectType);
        }
    };

    /** Whether a statically-known {@code declared} type is one this parameter takes. */
    abstract boolean accepts(final DataType declared);
}
