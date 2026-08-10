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

import dev.frostlake.executor.ValueComparisons;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.VariantType;

import java.util.List;

public class Greatest extends BuiltInFunction {
    public Greatest() { super("GREATEST", VariantType.VARIANT); }

    /**
     * {@code GREATEST} ORDERS its arguments, and a GEOSPATIAL value has no order — where an OBJECT
     * does. Live, {@code GREATEST(o, o)} returns the object while {@code GREATEST(g, g)} is
     * "Invalid argument types for function 'GREATEST': (GEOGRAPHY, GEOGRAPHY)" (SQLSTATE 42P13), and
     * {@code LEAST} splits the same way. This is the boundary of the NULL-choosing family rather than
     * a property of it: {@code IFF}, {@code COALESCE}, {@code NVL}, {@code IFNULL}, {@code NVL2},
     * {@code DECODE} and {@code CASE} all return a geo value happily, because none of them compares
     * one.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> rawArgs) {
        final List<Object> args = OrderingCoercion.coerceAll(rawArgs);
        if (args.isEmpty()) return null;

        Object max = null;
        for (final Object arg : args) {
            // Snowflake: LEAST/GREATEST return NULL when ANY argument is NULL (skipping them turned
            // LEAST(NULL, 100) into 100 — use LEAST_IGNORE_NULLS for the skipping behavior).
            if (arg == null) {
                return null;
            }
            if (max == null) {
                max = arg;
            } else if (ValueComparisons.compareValues(arg, max) > 0) {
                max = arg;
            }
        }
        return max;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
