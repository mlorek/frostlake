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

import dev.frostlake.functions.NumericArgumentFunction;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UNIFORM(min, max, gen) — a uniformly random value in the range. With exact bounds the result is drawn
 * at the larger of the bounds' scales, INCLUSIVE of both bounds — an integer for integer bounds, one
 * decimal for UNIFORM(1.5, 10, g) — and with a FLOAT bound it is a double in [min, max). The evaluator
 * hands the bounds over in the family and at the scale the call's type draws in. The generator argument
 * (typically RANDOM()) supplies the entropy: a per-row RANDOM() varies the result per row, a constant
 * RANDOM(seed) repeats it.
 */
public class Uniform extends NumericArgumentFunction {
    public Uniform() { super("UNIFORM", NumericType.NUMBER); }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null || args.get(1) == null) return null;
        final Object loArg = args.get(0);
        final Object hiArg = args.get(1);
        final long entropy = args.size() >= 3 && args.get(2) != null
            ? ((Number) args.get(2)).longValue()
            : ThreadLocalRandom.current().nextLong();

        if (isFloatBound(loArg) || isFloatBound(hiArg)) {
            final double lo = ((Number) loArg).doubleValue();
            final double hi = ((Number) hiArg).doubleValue();
            final double fraction = (entropy >>> 11) * 0x1.0p-53; // top 53 bits → [0, 1)
            return lo + fraction * (hi - lo);
        }
        if (loArg instanceof BigDecimal || hiArg instanceof BigDecimal) {
            final BigDecimal lo = new BigDecimal(loArg.toString());
            final BigDecimal hi = new BigDecimal(hiArg.toString());
            final int scale = Math.max(0, Math.max(lo.scale(), hi.scale()));
            final BigInteger loUnits = lo.setScale(scale).unscaledValue();
            final BigInteger range = hi.setScale(scale).unscaledValue().subtract(loUnits).add(BigInteger.ONE);
            final BigInteger units = range.signum() <= 0 ? loUnits
                : loUnits.add(BigInteger.valueOf(entropy).mod(range));
            return scale == 0 && units.bitLength() < 64 ? (Object) units.longValue() : new BigDecimal(units, scale);
        }
        final long lo = ((Number) loArg).longValue();
        final long hi = ((Number) hiArg).longValue();
        final long range = hi - lo + 1;
        return range <= 0 ? lo : lo + Math.floorMod(entropy, range);
    }

    /** Snowflake dispatches by bound TYPE: a FLOAT bound draws a double, an exact one draws at its scale. */
    private static boolean isFloatBound(final Object o) {
        return o instanceof Double || o instanceof Float;
    }

    // Snowflake requires the generator argument: UNIFORM(5, 10) errors "not enough arguments for
    // function [UNIFORM(5, 10)], expected 3, got 2" (live-verified) — RANDOM() is the usual third.
    @Override
    public int getMinArgCount() { return 3; }
    @Override
    public int getMaxArgCount() { return 3; }
}
