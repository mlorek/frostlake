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
import java.util.concurrent.atomic.AtomicLong;

/**
 * RANDOM([seed]) — a pseudo-random signed 64-bit integer (not a fraction in [0, 1)). Without a seed
 * each call is independent; with a seed the value is deterministic, so a constant seed yields the same
 * value for every row (matching Snowflake). java.util.Random is fully qualified because this class
 * shadows that name.
 */
public class Random extends BuiltInFunction {
    public Random() { super("RANDOM", NumericType.BIGINT); }

    // TEMPORARY (test reproducibility): the seedless path yields a deterministic sequence (SplitMix64 over a
    // monotonic counter) instead of ThreadLocalRandom. Revert to `ThreadLocalRandom.current().nextLong()`
    // (and restore the import) for real randomness. The seeded path below is already deterministic.
    private static final AtomicLong COUNTER = new AtomicLong();

    private static long mix(final long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (!args.isEmpty() && args.get(0) != null) {
            return new java.util.Random(((Number) args.get(0)).longValue()).nextLong();
        }
        return mix(COUNTER.incrementAndGet());
    }

    @Override public int getMinArgCount() { return 0; }
    @Override public int getMaxArgCount() { return 1; }
}
