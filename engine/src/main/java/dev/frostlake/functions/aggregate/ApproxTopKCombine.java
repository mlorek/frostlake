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
 * APPROX_TOP_K_COMBINE(state [, counters]): the APPROX_TOP_K_ACCUMULATE states of its input merged
 * into one — equal values add their counts, nothing is inherited — and trimmed to {@code counters}
 * (a constant between 1 and 100000; by default the inputs' own limit, which every input must share:
 * differing limits refuse with {@code Invalid parameter value: ApproxTopK state. Reason: combining
 * states with different numbers of counters}). The result's datatype and width are the first
 * state's; NULL inputs are skipped, and a combine that saw no state is the empty MISSING state with
 * one counter. An argument that is not a state refuses with {@code … Reason: invalid type}.
 */
public class ApproxTopKCombine extends AggregateFunction {

    public ApproxTopKCombine() {
        super("APPROX_TOP_K_COMBINE", ObjectType.OBJECT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new ApproxTopKCombineAccumulator();
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
        return 2;
    }
}
