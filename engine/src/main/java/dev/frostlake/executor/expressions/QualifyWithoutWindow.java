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

package dev.frostlake.executor.expressions;

/**
 * Whether the query being compiled on this thread, or one around it, writes a QUALIFY that no window
 * function serves — a query live refuses with {@code found QUALIFY clause but no window function.}.
 *
 * <p>That refusal outranks the checks live makes only once a query has been planned, so a rule of that
 * later phase stands aside while it is pending: ROUND's rounding mode is judged after it in every clause
 * (live-verified), {@code SELECT ROUND(n, 0, 'nosuch') FROM t QUALIFY 1 = 1} being the QUALIFY sentence.
 * Scoped to the thread like the other compilation modes, and inherited by the queries nested inside.
 */
public final class QualifyWithoutWindow {

    private static final ThreadLocal<Boolean> PENDING = new ThreadLocal<Boolean>();

    private QualifyWithoutWindow() {
    }

    /** Whether a QUALIFY without a window is waiting to be refused. */
    public static boolean isPending() {
        return Boolean.TRUE.equals(PENDING.get());
    }

    /**
     * Enter a query level.
     *
     * @param refused whether this level writes a QUALIFY without a window
     * @return the state to put back with {@link #end}
     */
    public static boolean begin(final boolean refused) {
        final boolean previous = isPending();
        if (refused) {
            PENDING.set(Boolean.TRUE);
        }
        return previous;
    }

    /** Leave a query level, putting back what {@link #begin} displaced. */
    public static void end(final boolean previous) {
        if (previous) {
            PENDING.set(Boolean.TRUE);
        } else {
            PENDING.remove();
        }
    }
}
