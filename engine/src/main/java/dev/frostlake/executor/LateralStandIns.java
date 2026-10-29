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
package dev.frostlake.executor;

/**
 * A lateral SQL table function's body read once more, its parameters standing for their arguments' static intervals:
 * the account tags such a function's columns by the statistics of the relation to its left, whatever rows the
 * function is called for (live-verified). The reading asks the table function planned next on this thread, and that
 * one alone - the calls its body makes are not asked - and tells whether a parameter took an interval.
 *
 * <p>Per thread: SELECTs run under the engine's read lock, so several evaluate at once.
 */
final class LateralStandIns {

    /** Per thread: whether the next table function is asked, and whether a parameter took an interval. */
    private static final ThreadLocal<boolean[]> STATE = new ThreadLocal<boolean[]>() {
        @Override
        protected boolean[] initialValue() {
            return new boolean[2];
        }
    };

    private LateralStandIns() {
    }

    /**
     * Asks the table function planned next on this thread to read its parameters as intervals.
     *
     * @return the state displaced, for {@link #end}
     */
    static boolean[] begin() {
        final boolean[] state = STATE.get();
        final boolean[] displaced = {state[0], state[1]};
        state[0] = true;
        state[1] = false;
        return displaced;
    }

    /**
     * Whether the table function being planned is asked; the asking ends with it.
     *
     * @return true when its parameters read as intervals
     */
    static boolean take() {
        final boolean[] state = STATE.get();
        final boolean asked = state[0];
        state[0] = false;
        return asked;
    }

    /** Records that a parameter read its argument's interval. */
    static void took() {
        STATE.get()[1] = true;
    }

    /**
     * Ends the reading {@link #begin} asked for.
     *
     * @param displaced the state begin displaced
     * @return whether a parameter read its argument's interval
     */
    static boolean end(final boolean[] displaced) {
        final boolean[] state = STATE.get();
        final boolean took = state[1];
        state[0] = displaced[0];
        state[1] = displaced[1];
        return took;
    }
}
