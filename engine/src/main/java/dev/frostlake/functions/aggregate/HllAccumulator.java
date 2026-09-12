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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * HyperLogLog sketch for APPROX_COUNT_DISTINCT (Flajolet et al.), with linear counting in the
 * small-cardinality range. Replaces the previous approach — an exact {@code HashSet} of 32-bit
 * {@code String.hashCode()} values — which could UNDERCOUNT whenever two distinct values collided on
 * hashCode (e.g. "Aa" and "BB"). Each value's string form is hashed to 64 bits, so distinct values are
 * (with overwhelming probability) counted separately. With {@code p = 14} registers the standard error
 * is ~0.8%, and linear counting keeps cardinalities up to ~40k (near-)exact.
 */
public class HllAccumulator implements AggregateFunction.Accumulator, MultiArgumentAccumulator {

    private static final int P = 14;
    private static final int M = 1 << P;                       // 16384 registers
    private static final double ALPHA = 0.7213 / (1.0 + 1.079 / M);

    private final byte[] registers = new byte[M];

    /**
     * A multi-argument call counts distinct TUPLES, and a tuple with any NULL member is skipped
     * whole — live counts (1.5, 7) once over {(1.5, 7), (1.5, NULL), (NULL, 7), (NULL, NULL), (1.5, 7)}.
     */
    @Override
    public void accumulate(final List<Object> argumentValues) {
        if (argumentValues.size() == 1) {
            accumulate(argumentValues.get(0));
            return;
        }
        final StringBuilder tuple = new StringBuilder();
        for (final Object member : argumentValues) {
            if (member == null) {
                return;
            }
            tuple.append(member.toString()).append('\u0001');
        }
        accumulateKey(tuple.toString());
    }

    @Override
    public void accumulate(final Object value) {
        if (value == null) {
            return;
        }
        accumulateKey(value.toString());
    }

    private void accumulateKey(final String key) {
        final long h = hash64(key);
        final int index = (int) (h >>> (64 - P));             // top P bits select the register
        final long remaining = (h << P) | (1L << (P - 1));    // bound the rank to <= 64 - P + 1
        final int rank = Long.numberOfLeadingZeros(remaining) + 1;
        if (rank > registers[index]) {
            registers[index] = (byte) rank;
        }
    }

    @Override
    public Object getResult() {
        double harmonic = 0.0;
        int zeroRegisters = 0;
        for (final byte r : registers) {
            harmonic += 1.0 / (1L << r);
            if (r == 0) {
                zeroRegisters++;
            }
        }
        double estimate = ALPHA * M * M / harmonic;
        // Small-cardinality correction: linear counting is far more accurate when many registers are
        // still empty. (The large-range correction is unnecessary with a 64-bit hash.)
        if (estimate <= 2.5 * M && zeroRegisters > 0) {
            estimate = M * Math.log((double) M / zeroRegisters);
        }
        return Math.round(estimate);
    }

    @Override
    public void reset() {
        Arrays.fill(registers, (byte) 0);
    }

    @Override
    public void merge(final AggregateFunction.Accumulator other) {
        final byte[] otherRegisters = ((HllAccumulator) other).registers;
        for (int i = 0; i < M; i++) {
            if (otherRegisters[i] > registers[i]) {
                registers[i] = otherRegisters[i];
            }
        }
    }

    /** 64-bit hash of a string: FNV-1a followed by a SplitMix64 finalizer for good bit avalanche. */
    private static long hash64(final String s) {
        long h = 0xcbf29ce484222325L;
        for (final byte b : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        h ^= (h >>> 30);
        h *= 0xbf58476d1ce4e5b9L;
        h ^= (h >>> 27);
        h *= 0x94d049bb133111ebL;
        h ^= (h >>> 31);
        return h;
    }
}
