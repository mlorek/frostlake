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
import dev.frostlake.types.ArrayType;

import java.util.List;

/**
 * APPROX_TOP_K(expr [, k [, counters]]): the k most frequent values of the input as an ARRAY of
 * [value, count] pairs, count descending, computed by the Space-Saving summary in
 * {@link TopKSummary} over {@code counters} counters (default 100000; k defaults to 1). NULLs are
 * not counted, an empty input is {@code []}, and a value keeps its own type inside the pair — a
 * NUMBER renders as a number, a VARCHAR as a string, a VARIANT as itself. The two limits are constants
 * between 1 and 100000, judged at compile time.
 *
 * <p>As a window function it belongs to the whole-partition family: the bare, PARTITION BY and
 * unbounded ROWS frames are answered, an ORDER BY's cumulative and sliding frames refused.
 */
public class ApproxTopK extends AggregateFunction {

    public ApproxTopK() {
        super("APPROX_TOP_K", ArrayType.ARRAY);
    }

    @Override
    public Accumulator createAccumulator() {
        return new ApproxTopKAccumulator();
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
        return 3;
    }
}
