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
 * The WHERE condition a filter is evaluating on this thread, for a correlated subquery written among its conjuncts:
 * an outer filter on the key a subquery is correlated by bounds the inner rows the account reads for it (see
 * {@link SubqueryEvaluator}). Each filter names its condition for the rows it reads and gives the one before it back
 * after them, so a nested query's filter holds its own condition only while it runs.
 */
public final class OuterFilters {

    private static final ThreadLocal<Expression> CURRENT = new ThreadLocal<Expression>();

    private OuterFilters() {
    }

    /**
     * Names the condition a filter is evaluating.
     *
     * @param condition the condition
     * @return the condition named before it, to give back to {@link #end}
     */
    public static Expression begin(final Expression condition) {
        final Expression displaced = CURRENT.get();
        CURRENT.set(condition);
        return displaced;
    }

    /**
     * Gives back the condition named before a filter's.
     *
     * @param displaced what {@link #begin} answered
     */
    public static void end(final Expression displaced) {
        if (displaced == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(displaced);
        }
    }

    /** @return the condition the innermost filter on this thread is evaluating, or null outside any */
    static Expression current() {
        return CURRENT.get();
    }
}
