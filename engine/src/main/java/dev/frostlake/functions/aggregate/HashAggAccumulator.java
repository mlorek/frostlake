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
import dev.frostlake.functions.MultiArgumentAccumulator;
import dev.frostlake.functions.scalar.hash.HashFn;

import java.util.ArrayList;
import java.util.List;

/**
 * Accumulator for {@link HashAgg}. Every row's ARGUMENT TUPLE is hashed with the shared scalar
 * {@link HashFn} (FNV-1a over the UTF-8 encoding, NULL included as a stable sentinel) and the per-row
 * hashes are summed. Summation is commutative and associative, so the accumulated hash is independent
 * of the order in which rows arrive and of how partial accumulators are
 * {@link #merge(AggregateFunction.Accumulator) merged}, while each occurrence still contributes
 * (duplicates are not cancelled out as they would be with XOR). An empty group yields 0, not NULL
 * (live-verified: HASH_AGG over zero rows is 0, while a group of NULLs is not — a NULL row contributes
 * like any other).
 *
 * <p>THE ROW HASH IS FOLDED ONCE MORE before it is added. Without that, a group of ONE row would
 * necessarily equal the scalar {@code HASH} of that row — and live's does not, in the one-argument and
 * the multi-argument form alike, so the two functions are not the same function seen through a group of
 * one. The fold is an FNV-1a pass over the row hash's own eight bytes: a bijection on 64 bits, so every
 * relation the sum gives — order independence, duplicates counting, a scale being invisible, a VARIANT
 * JSON null staying apart from SQL NULL — survives it untouched.
 */
public class HashAggAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator {

    private final HashFn hashFn = new HashFn();
    private long combined = 0L;

    @Override
    public void accumulate(final Object value) {
        final List<Object> single = new ArrayList<>(1);
        single.add(value);
        accumulateTuple(single);
    }

    /**
     * The multi-argument form: live folds EVERY argument of each row, so {@code HASH_AGG(a, b)} and
     * {@code HASH_AGG(a, b, c)} give different answers over the same rows. Reading only the first
     * argument silently dropped the rest.
     *
     * @param argumentValues one row's values, one per declared argument, in the order written
     */
    @Override
    public void accumulate(final List<Object> argumentValues) {
        accumulateTuple(argumentValues);
    }

    private void accumulateTuple(final List<Object> argumentValues) {
        combined += folded(((Number) hashFn.evaluate(argumentValues)).longValue());
    }

    /** One more FNV-1a pass over the row hash's eight bytes — see the class comment. */
    private static long folded(final long rowHash) {
        long fold = 0xcbf29ce484222325L;
        for (int shift = 56; shift >= 0; shift -= 8) {
            fold ^= rowHash >>> shift & 0xffL;
            fold *= 0x100000001b3L;
        }
        return fold;
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
