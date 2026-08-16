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

package dev.frostlake.functions.aggregate;

import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.types.ObjectType;

import java.util.List;

/**
 * APPROX_PERCENTILE_COMBINE(state): the APPROX_PERCENTILE_ACCUMULATE states of its input merged into
 * one digest — every centroid kept with its weight, sorted by mean. NULL inputs are skipped and a
 * combine that saw none is the empty state; an argument that is not a state refuses with
 * {@code First argument of the function must be an object which maps the key 'state' to an array}.
 */
public class ApproxPercentileCombine extends AggregateFunction {

    public ApproxPercentileCombine() {
        super("APPROX_PERCENTILE_COMBINE", ObjectType.OBJECT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new PercentileDigestCombineAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
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
