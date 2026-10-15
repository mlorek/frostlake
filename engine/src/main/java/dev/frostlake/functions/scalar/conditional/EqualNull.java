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

import dev.frostlake.executor.expressions.ExpressionArithmetic;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.BooleanType;

import java.util.List;

/** EQUAL_NULL(a, b) — NULL-safe equality: returns TRUE if both NULL, TRUE if equal, FALSE otherwise. */
public class EqualNull extends BuiltInFunction {
    public EqualNull() { super("EQUAL_NULL", BooleanType.BOOLEAN); }

    /**
     * A GEOSPATIAL value does not COMPARE, where an OBJECT does. Live,
     * {@code EQUAL_NULL(o, o)} returns TRUE while {@code EQUAL_NULL(g, g)} is "Invalid argument types
     * for function 'EQUAL_NULL': (GEOGRAPHY, GEOGRAPHY)" (SQLSTATE 42P13) — the same refusal the bare
     * {@code =} operator gives, and the sentence the {@code IS DISTINCT FROM} spelling reports too.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /**
     * Two NULLs are equal and a NULL beside a value is not; two values are equal exactly when {@code =} says so —
     * a VARIANT number beside a VARIANT string is not, two TIMESTAMP_TZ values at one instant are, and two intervals
     * of one family are when their spans are (live-verified). IS [NOT] DISTINCT FROM reads through here.
     */
    @Override
    public Object evaluate(final List<Object> args) {
        return ExpressionArithmetic.equals(args.get(0), args.get(1));
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
