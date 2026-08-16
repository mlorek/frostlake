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

import java.util.Locale;

/**
 * The words ROUND's optional third argument takes. Two, and only two: {@code HALF_AWAY_FROM_ZERO} —
 * the default, which rounds a tie away from zero — and {@code HALF_TO_EVEN}, banker's rounding. The
 * comparison is case-insensitive, live-verified in both cases, and nothing is trimmed: a word with a
 * space on either side names no mode.
 *
 * <p>Kept beside {@link Round}, which reads the same argument to pick a {@code RoundingMode}, and
 * shared with the compile-time check that refuses anything else: the two must agree about what a mode
 * IS, or a word one accepts would be refused by the other.
 */
public final class RoundingModeNames {

    private static final String HALF_AWAY_FROM_ZERO = "HALF_AWAY_FROM_ZERO";
    private static final String HALF_TO_EVEN = "HALF_TO_EVEN";

    private RoundingModeNames() {
    }

    /**
     * Whether {@code written} names a rounding mode.
     *
     * @param written the argument as it was written, quotes already removed
     * @return whether it is one of the two words
     */
    public static boolean isRoundingMode(final String written) {
        final String word = written.toUpperCase(Locale.ROOT);
        return HALF_AWAY_FROM_ZERO.equals(word) || HALF_TO_EVEN.equals(word);
    }
}
