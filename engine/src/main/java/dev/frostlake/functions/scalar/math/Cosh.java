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

import java.util.List;

public class Cosh extends NumericArgumentFunction {
    /** ln(2) / 2, below which the cosine is read from expm1. */
    private static final double HALF_LN2 = 0.34657359027997264;

    /** Past this the reciprocal exponential no longer counts. */
    private static final double RECIPROCAL_LIMIT = 22;

    /** ln(Double.MAX_VALUE), past which the exponential is taken in halves. */
    private static final double EXP_LIMIT = 709.782712893384;

    public Cosh() { super("COSH", NumericType.DOUBLE); }

    /**
     * The hyperbolic cosine as the account computes it: 1 + t^2 / (2 (1 + t)) over t = expm1(|x|) below ln(2) / 2,
     * half the exponential plus half its reciprocal below 22, half the exponential past it, and the exponential of
     * half the argument squared at the edge of the range. The JDK's own differs by a unit in the last place for some
     * arguments: {@code COSH(1)} is 1.5430806348152437 and {@code COSH(0.42)} 1.0895041877168452, where
     * {@code COSH(0.4)} stays 1.081072371838455 (live-verified).
     */
    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final double x = Math.abs(((Number) args.get(0)).doubleValue());
        if (Double.isNaN(x)) {
            return x;
        }
        if (x < HALF_LN2) {
            final double t = Math.expm1(x);
            final double w = 1 + t;
            return 1 + (t * t) / (w + w);
        }
        if (x < RECIPROCAL_LIMIT) {
            final double t = Math.exp(x);
            return 0.5 * t + 0.5 / t;
        }
        if (x < EXP_LIMIT) {
            return 0.5 * Math.exp(x);
        }
        final double w = Math.exp(0.5 * x);
        return 0.5 * w * w;
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
