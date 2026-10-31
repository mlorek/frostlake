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

/**
 * The draws a generator expression's value gives UNIFORM, NORMAL, ZIPF and RANDSTR, as the account makes them
 * (live-verified, every value of every seed measured): the value, times 2^64 over the golden ratio, is the state of a
 * 64-bit linear congruential engine with Knuth's MMIX multiplier and increment, and each draw hands out the state
 * before stepping it, so the generator 0 draws 0 first. The distributions read the draws as libstdc++'s do: a whole
 * number by downscaling, a fraction as a draw over 2^64, a normal value by Marsaglia's polar method.
 */
public final class GeneratorDraws {

    /** The engine's multiplier, Knuth's MMIX constant. */
    private static final long MULTIPLIER = 6364136223846793005L;

    /** The engine's increment, Knuth's MMIX constant. */
    private static final long INCREMENT = 1442695040888963407L;

    /** 2^64 over the golden ratio, which scatters neighbouring generator values across the engine's states. */
    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private long state;

    /**
     * The draws of one generator value.
     *
     * @param generator the generator expression's value, a whole number
     */
    public GeneratorDraws(final long generator) {
        this.state = generator * GOLDEN;
    }

    /**
     * The next draw.
     *
     * @return 64 bits, read as an unsigned number
     */
    public long next() {
        final long drawn = state;
        state = state * MULTIPLIER + INCREMENT;
        return drawn;
    }

    /**
     * A whole number in {@code lo..hi}, both included, as libstdc++'s uniform_int_distribution draws it from a 64-bit
     * engine: the draws are cut into as many equal runs as the range holds, a draw past the last whole run is drawn
     * again, and the run a draw falls in is the number.
     *
     * @param lo the least number
     * @param hi the greatest number, at least {@code lo}
     * @return the number
     */
    public long uniform(final long lo, final long hi) {
        final long width = hi - lo;
        if (width == -1L) {
            // The whole 64-bit range: every draw is a number.
            return next() + lo;
        }
        final long runs = width + 1L;
        final long scaling = Long.divideUnsigned(-1L, runs);
        final long past = runs * scaling;
        long drawn = next();
        while (Long.compareUnsigned(drawn, past) >= 0) {
            drawn = next();
        }
        return lo + Long.divideUnsigned(drawn, scaling);
    }

    /**
     * A fraction in [0, 1): the next draw over 2^64, rounded to the nearest double, as libstdc++'s generate_canonical
     * makes it, and just under 1 where that rounds up to 1.
     *
     * @return the fraction
     */
    public double canonical() {
        final double fraction = unsigned(next()) * 0x1.0p-64;
        return fraction < 1.0 ? fraction : Math.nextDown(1.0);
    }

    /**
     * A standard normal value, as libstdc++'s normal_distribution draws one: Marsaglia's polar method over pairs of
     * fractions mapped to [-1, 1), the first pair inside the unit circle and not at its centre giving two values, of
     * which the second is answered.
     *
     * @return the value
     */
    public double normal() {
        double x = 2.0 * canonical() - 1.0;
        double y = 2.0 * canonical() - 1.0;
        double squared = x * x + y * y;
        while (squared > 1.0 || squared == 0.0) {
            x = 2.0 * canonical() - 1.0;
            y = 2.0 * canonical() - 1.0;
            squared = x * x + y * y;
        }
        return y * Math.sqrt(-2.0 * Math.log(squared) / squared);
    }

    /** An unsigned 64-bit number as the nearest double: halved with its last bit kept, so it rounds as itself. */
    private static double unsigned(final long bits) {
        return bits >= 0 ? (double) bits : (double) ((bits >>> 1) | (bits & 1L)) * 2.0;
    }
}
