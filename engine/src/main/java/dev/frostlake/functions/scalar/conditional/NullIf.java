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

import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.ExpressionArithmetic;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.VariantType;

import java.util.List;

public class NullIf extends BuiltInFunction {
    public NullIf() { super("NULLIF", VariantType.VARIANT); }

    /**
     * {@code NULLIF} compares its two arguments, and a GEOSPATIAL value does not compare: live
     * {@code NULLIF(g, g)} is "Invalid argument types for function 'NULLIF': (GEOGRAPHY,
     * GEOGRAPHY)" (SQLSTATE 42P13), while {@code COALESCE(g, NULL)} — which chooses without comparing
     * — returns the geo value.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /**
     * {@code NULLIF(a, b)} is {@code CASE WHEN a = b THEN NULL ELSE a END}, so it decides equality the
     * way the {@code =} operator does — by VALUE, across the type families. Deciding it with
     * {@link Object#equals} instead made the answer depend on a runtime class the SQL cannot see:
     * {@code NULLIF(1.00, 1)} handed back 1.00 because a BigDecimal is not a Long, and the same split
     * hid every mixed-family pair — a boolean beside a number, a numeric string beside a number, a
     * DATE beside a TIMESTAMP at the same instant. A NULL on either side is never compared: SQL NULL
     * is not equal to anything, so the first argument stands.
     */
    @Override
    public Object evaluate(final List<Object> args) {
        final Object val1 = args.get(0);
        final Object val2 = args.get(1);
        if (val1 == null || val2 == null) return val1;
        if (ExpressionArithmetic.equals(val1, val2)) return null;
        return val1;
    }

    /** Under a collation the two are compared by its rules, so a case-blind pair answers NULL. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null || args.get(0) == null || args.get(1) == null) {
            return evaluate(args);
        }
        return CollationMatching.equalUnder(collation, args.get(0), args.get(1)) ? null : args.get(0);
    }

    @Override
    public int getMinArgCount() { return 2; }
    @Override
    public int getMaxArgCount() { return 2; }
}
