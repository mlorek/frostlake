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

public class Cbrt extends NumericArgumentFunction {
    public Cbrt() { super("CBRT", NumericType.DOUBLE); }

    private static final double CBRT_OF_TWO = 1.2599210498948731648;
    private static final double CBRT_OF_FOUR = 1.5874010519681994748;
    /** What the refined root is multiplied by for each remainder of the exponent divided by three, -2 to 2. */
    private static final double[] FACTOR = {1.0 / CBRT_OF_FOUR, 1.0 / CBRT_OF_TWO, 1.0, CBRT_OF_TWO, CBRT_OF_FOUR};
    /** A subnormal number is scaled up by this many binary places before its exponent is read. */
    private static final int SUBNORMAL_BITS = 54;
    private static final double SUBNORMAL_SCALE = 0x1p54;

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        return rootOf(((Number) args.get(0)).doubleValue());
    }

    /**
     * The cube root as the account's platform computes it: a polynomial estimate of the root of the value's
     * fraction, refined once, and scaled back by the exponent's third. It is within a unit in the last place of
     * the true root and not always the nearest double, so a perfect cube may miss its root by the last digit:
     * the root of 27 is 3.0000000000000004 and that of 216 is 6.000000000000001, where those of 64 and 125 are
     * exact (live-verified).
     *
     * @param value the number
     * @return its cube root
     */
    static double rootOf(final double value) {
        if (value == 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return value;
        }
        double magnitude = Math.abs(value);
        int exponent = 0;
        if (magnitude < Double.MIN_NORMAL) {
            magnitude = magnitude * SUBNORMAL_SCALE;
            exponent = -SUBNORMAL_BITS;
        }
        // The fraction lies in [0.5, 1) and the value is the fraction times two to the exponent.
        final int binary = Math.getExponent(magnitude) + 1;
        final double fraction = Math.scalb(magnitude, -binary);
        exponent += binary;
        final double estimate = 0.354895765043919860
            + ((1.50819193781584896
                - ((2.11499494167371287
                    - ((2.44693122563534430
                        - ((1.83469277483613086
                            - (0.784932344976639262 - 0.145263899385486377 * fraction)
                            * fraction)
                           * fraction))
                       * fraction))
                   * fraction))
               * fraction);
        final double cube = estimate * estimate * estimate;
        final double refined = estimate * (cube + 2.0 * fraction) / (2.0 * cube + fraction)
            * FACTOR[2 + exponent % 3];
        return Math.scalb(value > 0 ? refined : -refined, exponent / 3);
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }
}
