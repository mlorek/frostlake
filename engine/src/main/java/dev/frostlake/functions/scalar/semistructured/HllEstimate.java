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

package dev.frostlake.functions.scalar.semistructured;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.aggregate.HllSketch;
import dev.frostlake.functions.aggregate.HllStates;
import dev.frostlake.types.NumericType;

import java.util.List;

/**
 * {@code HLL_ESTIMATE(state)} — the cardinality a HyperLogLog state stands for. A NULL state answers NULL
 * and an EMPTY state answers 0, which is what a group with no value accumulates to (live-verified).
 */
public class HllEstimate extends BuiltInFunction {

    /** Registers the function as {@code HLL_ESTIMATE} returning NUMBER(18,0). */
    public HllEstimate() {
        super("HLL_ESTIMATE", NumericType.BIGINT);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final Object state = args.get(0);
        if (state == null) {
            return null;
        }
        final HllSketch sketch = HllStates.sketchOf(state);
        return sketch == null ? Long.valueOf(0L) : Long.valueOf(sketch.estimate());
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
