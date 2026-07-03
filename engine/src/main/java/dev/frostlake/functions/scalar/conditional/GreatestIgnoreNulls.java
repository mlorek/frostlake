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
import dev.frostlake.types.VariantType;

import java.util.List;

public class GreatestIgnoreNulls extends BuiltInFunction {
    public GreatestIgnoreNulls() { super("GREATEST_IGNORE_NULLS", VariantType.VARIANT); }

    @Override
    public Object evaluate(final List<Object> args) {
        Object max = null;
        for (final Object a : args) {
            if (a == null) continue;
            if (max == null || compareObj(a, max) > 0) max = a;
        }
        return max;
    }

    @SuppressWarnings("unchecked")
    private int compareObj(final Object a, final Object b) {
        if (a instanceof Number && b instanceof Number) return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        return a.toString().compareTo(b.toString());
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
