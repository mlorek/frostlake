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
import dev.frostlake.functions.scalar.hash.HashFn;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for {@link HashAgg}. Every row's value is hashed with the shared scalar {@link HashFn}
 * (FNV-1a over the UTF-8 encoding, NULL included as a stable sentinel) and the per-row hashes are summed.
 * Summation is commutative and associative, so the accumulated hash is independent of the order in which
 * rows arrive and of how partial accumulators are {@link #merge(AggregateFunction.Accumulator) merged},
 * while each occurrence still contributes (duplicates are not cancelled out as they would be with XOR).
 * An empty group yields 0, not NULL (live-verified: HASH_AGG over zero rows is 0).
 */
public class HashAggAccumulator implements AggregateFunction.Accumulator {

    private final HashFn hashFn = new HashFn();
    private long combined = 0L;

    @Override
    public void accumulate(final Object value) {
        final List<Object> single = new ArrayList<>(1);
        single.add(value);
        combined += ((Number) hashFn.evaluate(single)).longValue();
    }

    @Override
    public Object getResult() {
        return Long.valueOf(combined);
    }

    @Override
    public void reset() {
        combined = 0L;
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        combined += ((HashAggAccumulator) other).combined;
    }
}
