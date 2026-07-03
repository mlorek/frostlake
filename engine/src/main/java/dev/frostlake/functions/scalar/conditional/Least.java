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

public class Least extends BuiltInFunction {
    public Least() { super("LEAST", VariantType.VARIANT); }

    @Override
    @SuppressWarnings("unchecked")
    public Object evaluate(final List<Object> args) {
        if (args.isEmpty()) return null;

        Object min = null;
        for (final Object arg : args) {
            if (arg == null) continue;
            if (min == null) {
                min = arg;
            } else if (arg instanceof Comparable && min instanceof Comparable) {
                Comparable compArg = (Comparable) arg;
                Comparable compMin = (Comparable) min;
                if (compArg.compareTo(compMin) < 0) {
                    min = arg;
                }
            }
        }
        return min;
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return Integer.MAX_VALUE; }
}
