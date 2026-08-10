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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.BooleanType;

import java.math.BigDecimal;
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

    @Override
    public Object evaluate(final List<Object> args) {
        final Object a = args.get(0);
        final Object b = args.get(1);
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        if (a instanceof Number && b instanceof Number) {
            return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        }
        return a.toString().equals(b.toString());
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
