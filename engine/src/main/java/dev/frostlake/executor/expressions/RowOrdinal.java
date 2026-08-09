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
 * Which row, counting from 0, the operator is currently evaluating expressions for — what the
 * {@code SEQ1} / {@code SEQ2} / {@code SEQ4} / {@code SEQ8} family reads.
 *
 * <p>The ordinal belongs to the ROW, not to the call. Measured live, two {@code SEQ4()} calls in one
 * row return the SAME number ({@code SEQ4() + SEQ4()} yields 0, 2, 4, 6 — not 0+1, 2+3), and a
 * {@code SEQ4()} in a subquery agrees with one in the block above it. A counter incremented inside
 * the function — the shape {@code RANDOM} and {@code UUID_STRING} use — would number the CALLS and
 * get every one of those wrong, which is why the count lives out here and the function only reads it.
 *
 * <p>Set once per row by the operator that iterates rows, so all of a row's expressions see one
 * value. That also places the count where Snowflake places it: an operator numbers the rows it is
 * HANDED, so a WHERE that ran earlier in the pipeline has already removed rows and the projection
 * numbers the survivors from 0 — live, {@code SELECT x, SEQ4() FROM t WHERE x > 4} over 1..8 returns
 * {@code 5|0 6|1 7|2 8|3}.
 *
 * <p>Scoped to the thread, like {@link ExpressionSource} and the statement clock, because a session's
 * statements run on the caller's thread. Unset means row 0: a {@code SELECT} with no FROM never
 * reaches an operator at all, and live answers {@code SEQ4()} there with 0.
 */
public final class RowOrdinal {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<Long>();

    private RowOrdinal() {
    }

    /**
     * Note the row about to be evaluated, returning whatever ordinal it displaced so a nested
     * evaluation — a scalar subquery runs its own operators mid-row — can put it back.
     */
    public static Long begin(final long ordinal) {
        final Long previous = CURRENT.get();
        CURRENT.set(Long.valueOf(ordinal));
        return previous;
    }

    /** Put back whatever {@link #begin} displaced; null clears. */
    public static void end(final Long previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /** The current row's 0-based ordinal, or 0 when nothing has set one. */
    public static long current() {
        final Long ordinal = CURRENT.get();
        return ordinal == null ? 0L : ordinal.longValue();
    }
}
