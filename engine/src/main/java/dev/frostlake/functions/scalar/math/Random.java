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

import dev.frostlake.executor.expressions.VariantNumbers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.VariantValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    // The account declares RANDOM() as NUMBER(19,0) — SYSTEM$TYPEOF(RANDOM()) reads NUMBER(19,0)[SB8],
    // and a refusal listing it as an argument spells the same pair.
    public Random() { super("RANDOM", new NumericType("NUMBER", 19, 0)); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (!args.isEmpty()) {
            return new java.util.Random(seedOf(args.get(0))).nextLong();
        }
        return ThreadLocalRandom.current().nextLong();
    }

    /**
     * The seed as a whole number, rounded half away from zero — {@code RANDOM(1.5)}, {@code RANDOM('1.5')} and
     * {@code RANDOM(1.5::FLOAT)} are {@code RANDOM(2)} (live-verified). A text seed is read as the number it
     * spells; a VARIANT reads through its member as a cast to FIXED does, a boolean as 1 or 0, and anything
     * else fails that cast. A NULL seed, SQL or JSON, is refused when the row reads it.
     */
    private static long seedOf(final Object seed) {
        final Object value = seed instanceof VariantValue
            ? VariantNumbers.numberOf((VariantValue) seed, VariantNumbers.FIXED) : seed;
        if (value == null) {
            throw new RuntimeException("Invalid parameter value: NULL. Reason: seed must not be NULL");
        }
        if (value instanceof Double && !Double.isFinite(((Double) value).doubleValue())
                || value instanceof Float && !Float.isFinite(((Float) value).floatValue())) {
            return ((Number) value).longValue();
        }
        final BigDecimal number;
        if (value instanceof BigDecimal) {
            number = (BigDecimal) value;
        } else if (value instanceof Number) {
            number = new BigDecimal(value.toString());
        } else {
            try {
                number = new BigDecimal(value.toString().trim());
            } catch (final NumberFormatException notANumber) {
                throw new RuntimeException("Numeric value '" + value + "' is not recognized");
            }
        }
        return number.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** An ARRAY or OBJECT seed is refused by the argument types while the statement compiles (live-verified). */
    @Override
    public SemiStructuredRejection semiStructuredRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** A BOOLEAN seed is refused by the argument types while the statement compiles (live-verified). */
    @Override
    public SemiStructuredRejection booleanRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    /** So is a DATE, a TIME or a TIMESTAMP seed: RANDOM(CURRENT_DATE()) is 'RANDOM': (DATE) (live-verified). */
    @Override
    public SemiStructuredRejection temporalRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }

    @Override
    public int getMinArgCount() { return 0; }
    @Override
    public int getMaxArgCount() { return 1; }
}
