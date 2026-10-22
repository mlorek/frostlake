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
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * GREATEST, LEAST and their IGNORE_NULLS spellings: the extreme of the arguments under one order, the first of
 * several that tie — {@code -0.0} and {@code 0.0} tie, so {@code GREATEST_IGNORE_NULLS(-0.0::FLOAT, 0.0::FLOAT)} is
 * {@code -0} and {@code LEAST_IGNORE_NULLS(0.0::FLOAT, -0.0::FLOAT)} is {@code 0}, as for the plain spellings
 * (live-verified). The plain spellings answer NULL when any argument is NULL — {@code LEAST(NULL, 100)} is NULL —
 * and the IGNORE_NULLS ones skip it, answering NULL only when every argument is.
 */
public abstract class ArgumentExtremeFunction extends BuiltInFunction {

    /** Whether the largest argument is the answer, else the smallest. */
    private final boolean greatest;
    /** Whether a NULL argument is skipped rather than answered. */
    private final boolean skipsNulls;

    protected ArgumentExtremeFunction(final String name, final boolean greatest, final boolean skipsNulls) {
        super(name, VariantType.VARIANT);
        this.greatest = greatest;
        this.skipsNulls = skipsNulls;
    }

    /**
     * The function ORDERS its arguments, and a GEOSPATIAL value has no order — where an OBJECT does. Live,
     * {@code GREATEST(o, o)} returns the object while {@code GREATEST(g, g)} is "Invalid argument types for function
     * 'GREATEST': (GEOGRAPHY, GEOGRAPHY)" (SQLSTATE 42P13), and LEAST and both IGNORE_NULLS spellings split the same
     * way. This is the boundary of the NULL-choosing family rather than a property of it: {@code IFF},
     * {@code COALESCE}, {@code NVL}, {@code IFNULL}, {@code NVL2}, {@code DECODE} and {@code CASE} all return a geo
     * value happily, because none of them compares one.
     */
    @Override
    public SemiStructuredRejection geoRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return extremeOf(args, null);
    }

    /** Under a collation the arguments are ordered by its rules, so 'a' outranks 'B' where it should. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        return extremeOf(args, collation);
    }

    private Object extremeOf(final List<Object> args, final CollationSpec collation) {
        Object extreme = null;
        for (final Object arg : OrderingCoercion.coerceAll(args)) {
            if (arg == null) {
                if (skipsNulls) {
                    continue;
                }
                return null;
            }
            if (extreme == null) {
                extreme = arg;
                continue;
            }
            final int order = compare(collation, arg, extreme);
            if (greatest ? order > 0 : order < 0) {
                extreme = arg;
            }
        }
        return extreme;
    }

    /** The extreme's comparison, by the collation when there is one and both sides are text. */
    private static int compare(final CollationSpec collation, final Object left, final Object right) {
        if (collation != null && left instanceof String && right instanceof String) {
            return collation.compare((String) left, (String) right);
        }
        return ValueComparisons.compareForExtreme(left, right);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
