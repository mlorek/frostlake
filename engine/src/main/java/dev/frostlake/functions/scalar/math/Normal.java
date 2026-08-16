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

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.NumericType;

import java.util.List;

public class Normal extends BuiltInFunction {
    private static final java.util.Random RNG = new java.util.Random();

    public Normal() { super("NORMAL", NumericType.DOUBLE); }

    @Override
    public Object evaluate(final List<Object> args) {
        final double mean   = args.size() > 0 && args.get(0) != null ? ((Number) args.get(0)).doubleValue() : 0.0;
        final double stddev = args.size() > 1 && args.get(1) != null ? ((Number) args.get(1)).doubleValue() : 1.0;
        return mean + stddev * RNG.nextGaussian();
    }

    // Exactly three arguments. Live refuses NORMAL(0, 1) AND NORMAL(0::FLOAT, 1::FLOAT) while
    // accepting NORMAL(0, 1, RANDOM()) with plain integers, so it is the ARITY that matters — the
    // message names the argument TYPES, which is misleading.
    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
