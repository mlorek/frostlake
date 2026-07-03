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

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * UNIFORM(min, max [, gen]) — a uniformly random value in the range. With integer bounds the result is
 * an integer INCLUSIVE of both bounds (Snowflake); with a floating-point bound it is a double in
 * [min, max). The optional generator argument (typically RANDOM()) supplies the entropy — a per-row
 * RANDOM() varies the result per row, a constant RANDOM(seed) repeats it; without it an independent
 * value is used per call.
 */
public class Uniform extends BuiltInFunction {
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
        final long lo = ((Number) loArg).longValue();
        final long hi = ((Number) hiArg).longValue();
        final long range = hi - lo + 1;
        return range <= 0 ? lo : lo + Math.floorMod(entropy, range);
    }

    /** Snowflake dispatches by bound TYPE: a Double/Float, or a BigDecimal with a fractional scale. */
    private static boolean isFloatBound(final Object o) {
        if (o instanceof Double || o instanceof Float) {
            return true;
        }
        return o instanceof BigDecimal && ((BigDecimal) o).scale() > 0;
    }

    @Override public int getMinArgCount() { return 2; }
    @Override public int getMaxArgCount() { return 3; }
}
