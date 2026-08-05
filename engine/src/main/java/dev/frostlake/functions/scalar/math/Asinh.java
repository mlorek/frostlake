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

package dev.frostlake.functions.scalar.math;

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * ASINH(x) — inverse hyperbolic sine, computed as {@code ln(x + sqrt(x*x + 1))} since {@link Math} has no
 * {@code asinh}. Defined for all real x; NULL yields NULL.
 */
public class Asinh extends NumericArgumentFunction {
    public Asinh() { super("ASINH", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final double x = ((Number) args.get(0)).doubleValue();
        return Math.log(x + Math.sqrt(x * x + 1));
    }

    @Override public int getMinArgCount() { return 1; }
    @Override public int getMaxArgCount() { return 1; }
}
