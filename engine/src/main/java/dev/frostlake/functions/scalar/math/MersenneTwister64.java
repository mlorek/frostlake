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
 * The 64-bit Mersenne Twister, std::mt19937_64, which a seeded RANDOM draws from as the account's does
 * (live-verified): the seed starts the engine, and the row a call stands in takes the engine's output of that place,
 * the first row the first output.
 */
public final class MersenneTwister64 {

    private static final int WORDS = 312;
    private static final int SHIFT = 156;
    private static final long MATRIX = 0xB5026F5AA96619E9L;
    private static final long UPPER = 0xFFFFFFFF80000000L;
    private static final long LOWER = 0x7FFFFFFFL;
    private static final long SEED_MULTIPLIER = 6364136223846793005L;

    /**
     * The engine a thread last drew from, kept with its seed and place: rows are numbered in order, so the next row's
     * output is one step on.
     */
    private static final ThreadLocal<MersenneTwister64> LAST = new ThreadLocal<MersenneTwister64>();

    private final long seed;
    private final long[] words = new long[WORDS];
    private int index;
    /** How many outputs the engine has handed out. */
    private long drawn;

    private MersenneTwister64(final long seed) {
        this.seed = seed;
        words[0] = seed;
        for (int i = 1; i < WORDS; i++) {
            words[i] = SEED_MULTIPLIER * (words[i - 1] ^ (words[i - 1] >>> 62)) + i;
        }
        index = WORDS;
    }

    /**
     * The engine's output at a place for a seed.
     *
     * @param seed  the seed
     * @param place how many outputs come before it, from 0
     * @return the output, a signed 64-bit number
     */
    public static long outputAt(final long seed, final long place) {
        MersenneTwister64 engine = LAST.get();
        if (engine == null || engine.seed != seed || engine.drawn > place) {
            engine = new MersenneTwister64(seed);
            LAST.set(engine);
        }
        while (engine.drawn < place) {
            engine.next();
        }
        return engine.next();
    }

    private long next() {
        if (index >= WORDS) {
            twist();
        }
        long y = words[index++];
        y ^= (y >>> 29) & 0x5555555555555555L;
        y ^= (y << 17) & 0x71D67FFFEDA60000L;
        y ^= (y << 37) & 0xFFF7EEE000000000L;
        y ^= y >>> 43;
        drawn++;
        return y;
    }

    private void twist() {
        for (int i = 0; i < WORDS; i++) {
            final long y = (words[i] & UPPER) | (words[(i + 1) % WORDS] & LOWER);
            words[i] = words[(i + SHIFT) % WORDS] ^ (y >>> 1) ^ ((y & 1L) == 0 ? 0L : MATRIX);
        }
        index = 0;
    }
}
