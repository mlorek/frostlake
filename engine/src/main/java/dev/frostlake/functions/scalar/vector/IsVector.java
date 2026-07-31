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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BooleanType;
import dev.frostlake.values.VectorValue;

import java.util.List;

/**
 * {@code IS_VECTOR(x)} — TRUE when the value is a VECTOR, FALSE for every other value VARIANT can hold.
 *
 * <p>Live-verified: TRUE for a {@code VECTOR(FLOAT,3)} and a {@code VECTOR(INT,3)}, FALSE for
 * a plain ARRAY ({@code IS_VECTOR([1,2,3])}), an OBJECT, a {@code TO_VARIANT}/{@code PARSE_JSON} value,
 * a NUMBER and a BOOLEAN — and it keeps working through a derived table
 * ({@code SELECT IS_VECTOR(v) FROM (SELECT [1,2,3]::VECTOR(FLOAT,3) AS v)} is TRUE), which is why the
 * answer comes from the runtime value's own type rather than from static inference alone.
 *
 * <p>NULL in is NULL OUT, not FALSE: both {@code IS_VECTOR(NULL)} and
 * {@code IS_VECTOR(NULL::VECTOR(FLOAT,3))} are SQL NULL. A VARCHAR or a temporal argument is not a
 * value VARIANT can hold at all and is the compile error "Invalid argument types for function
 * 'IS_VECTOR': (VARCHAR(3))" — enforced by the expression layer's variant-coercibility rule.
 */
public class IsVector extends BuiltInFunction {

    public IsVector() {
        super("IS_VECTOR", BooleanType.BOOLEAN);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) {
            return null;
        }
        return Boolean.valueOf(args.get(0) instanceof VectorValue);
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
