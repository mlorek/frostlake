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
 * APPROX_TOP_K_ACCUMULATE(expr, counters): the whole {@link TopKSummary} of the input as an OBJECT
 * state — {@code {"counters":C,"datatype":T,"precision":P,"scale":S,"state":[[value, count], …],
 * "type":"approx_top_k"}} — for APPROX_TOP_K_COMBINE to merge and APPROX_TOP_K_ESTIMATE to rank. Both
 * arguments are required; the counter limit is a constant between 1 and 100000. The datatype is the
 * input's declared type in the catalog's internal vocabulary (FIXED, TEXT, REAL, DATE, BOOLEAN,
 * VARIANT, …), with a NUMBER's own precision and scale and 38/0 for everything else; a state that saw
 * no value reports one counter and the 38/0 width whatever was asked (live-verified).
 */
public class ApproxTopKAccumulate extends AggregateFunction {

    public ApproxTopKAccumulate() {
        super("APPROX_TOP_K_ACCUMULATE", ObjectType.OBJECT);
    }

    @Override
    public Accumulator createAccumulator() {
        return new ApproxTopKStateAccumulator();
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return null;
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
