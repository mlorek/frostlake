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
import java.util.concurrent.ThreadLocalRandom;

/**
 * RANDOM([seed]) — a pseudo-random signed 64-bit integer (not a fraction in [0, 1)). Without a seed
 * each call is independent; with a seed the value is deterministic, so a constant seed yields the same
 * value for every row (matching Snowflake). java.util.Random is fully qualified because this class
 * shadows that name.
 *
 * <p>The seedless path is genuinely random, as Snowflake's is. It once returned a SplitMix64 scramble
 * of a monotonic counter for test reproducibility; that was removed because nothing depended on it —
 * no test asserts a value (they assert type, range and uniqueness, which real randomness satisfies and
 * the counter satisfied only trivially) and the vendor suite never calls the function. The counter also
 * restarted at zero in a fresh JVM, so it never delivered the WAL-replay exactness it appeared to
 * promise: a replayed statement drew new values either way. Reproducing values across replay is a
 * durability question about the log, not something a function can answer for itself.
 */
public class Random extends BuiltInFunction {
    public Random() { super("RANDOM", NumericType.BIGINT); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (!args.isEmpty() && args.get(0) != null) {
            return new java.util.Random(((Number) args.get(0)).longValue()).nextLong();
        }
        return ThreadLocalRandom.current().nextLong();
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 1; }
}
