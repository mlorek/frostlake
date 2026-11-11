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

package dev.frostlake.functions.scalar;

/**
 * The fraction digits a bare FF prints while one value is formatted: the scale of the value's own type, a
 * TIMESTAMP_NTZ(3)'s three digits, a TIME(1)'s one, a scale of 0's none (live-verified, TO_CHAR over each type). Per
 * thread, as statements run on several at once; -1 while no scale is known, and FF then prints nine digits.
 */
public final class FractionScale {

    private static final ThreadLocal<int[]> SCALE = new ThreadLocal<int[]>() {
        @Override
        protected int[] initialValue() {
            return new int[] {-1};
        }
    };

    private FractionScale() {
    }

    /**
     * Sets the scale a bare FF prints until {@link #exit}.
     *
     * @param scale the digits, or -1 for none known
     * @return the scale displaced, for exit
     */
    public static int enter(final int scale) {
        final int[] held = SCALE.get();
        final int displaced = held[0];
        held[0] = scale;
        return displaced;
    }

    /**
     * Puts back the scale {@link #enter} displaced.
     *
     * @param displaced the scale enter returned
     */
    public static void exit(final int displaced) {
        SCALE.get()[0] = displaced;
    }

    /** @return the scale a bare FF prints, or -1 for nine digits */
    public static int current() {
        return SCALE.get()[0];
    }
}
