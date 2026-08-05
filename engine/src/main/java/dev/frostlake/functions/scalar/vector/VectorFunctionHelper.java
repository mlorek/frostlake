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

package dev.frostlake.functions.scalar.vector;

import dev.frostlake.values.VectorValue;

import java.util.List;

/**
 * Argument handling shared by the VECTOR functions.
 *
 * <p>Snowflake decides these functions' legality from the STATIC types, so the argument checks fire at
 * COMPILE time — Frostlake runs them in the expression layer's strict-argument walk (which sees the
 * declared types) and this helper is the RUNTIME backstop for the cases static inference cannot reach:
 * a derived column, a CTE column, a UDF parameter. Live those keep the vector type all the
 * same — {@code SELECT IS_VECTOR(v) FROM (SELECT [1,2,3]::VECTOR(FLOAT,3) AS v)} is TRUE — which is why
 * the runtime value carries its own element type and dimension.
 */
public final class VectorFunctionHelper {

    private VectorFunctionHelper() {
    }

    /**
     * The argument as a vector, or null when the value is SQL NULL (every one of these functions is
     * NULL-in / NULL-out: live, {@code VECTOR_NORMALIZE(NULL::VECTOR(FLOAT,3))} and
     * {@code VECTOR_L2_DISTANCE(NULL::VECTOR(FLOAT,3), v)} are both NULL).
     */
    public static VectorValue vectorArgument(final String funcName, final List<Object> args, final int index) {
        final Object value = args.get(index);
        if (value == null) {
            return null;
        }
        if (value instanceof VectorValue) {
            return (VectorValue) value;
        }
        throw new RuntimeException("Invalid argument types for function '" + funcName + "': ("
            + argumentTypeNames(args) + ")");
    }

    /**
     * Both operands of a two-vector function must have the SAME element type and dimension — live,
     * {@code VECTOR_L2_DISTANCE(<VECTOR(FLOAT,3)>, <VECTOR(INT,3)>)} and a 2-vs-3 dimension pair are
     * both "Invalid argument types for function 'VECTOR_L2_DISTANCE': (…)".
     */
    public static void requireSameType(final String funcName, final List<Object> args,
                                       final VectorValue left, final VectorValue right) {
        if (!left.type().equals(right.type())) {
            throw new RuntimeException("Invalid argument types for function '" + funcName + "': ("
                + argumentTypeNames(args) + ")");
        }
    }

    /** The argument types as Snowflake lists them in the error — vectors by their full parameterization. */
    private static String argumentTypeNames(final List<Object> args) {
        final StringBuilder text = new StringBuilder();
        for (final Object arg : args) {
            if (text.length() > 0) {
                text.append(", ");
            }
            if (arg == null) {
                text.append("NULL");
            } else if (arg instanceof VectorValue) {
                text.append(((VectorValue) arg).type().getName());
            } else {
                text.append("ARRAY");
            }
        }
        return text.toString();
    }
}
