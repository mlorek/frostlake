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
import dev.frostlake.types.VariantType;

import java.util.List;

/**
 * GREATEST_IGNORE_NULLS — the largest non-NULL argument, IGNORING NULLs (Snowflake's explicit skipping variant; plain
 * LEAST/GREATEST return NULL when any argument is NULL). NULL only when every argument is NULL.
 */
public class GreatestIgnoreNulls extends BuiltInFunction {

    public GreatestIgnoreNulls() { super("GREATEST_IGNORE_NULLS", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> rawArgs) {
        final List<Object> args = OrderingCoercion.coerceAll(rawArgs);
        if (args.isEmpty()) {
            return null;
        }
        Object max = null;
        for (final Object arg : args) {
            if (arg == null) {
                continue;
            }
            if (max == null) {
                max = arg;
            } else if (ValueComparisons.compareValues(arg, max) > 0) {
                max = arg;
            }
        }
        return max;
    }

    /** Under a collation the arguments are ordered by its rules, so 'a' outranks 'B' where it should. */
    @Override
    public Object evaluate(final List<Object> args, final CollationSpec collation) {
        if (collation == null) {
            return evaluate(args);
        }
        Object max = null;
        for (final Object arg : OrderingCoercion.coerceAll(args)) {
            if (arg == null) {
                continue;
            }
            if (max == null || compareUnder(collation, arg, max) > 0) {
                max = arg;
            }
        }
        return max;
    }

    /** The extreme's comparison, by the collation when both sides are text. */
    private static int compareUnder(final CollationSpec collation, final Object left, final Object right) {
        if (left instanceof String && right instanceof String) {
            return collation.compare((String) left, (String) right);
        }
        return ValueComparisons.compareForExtreme(left, right);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
