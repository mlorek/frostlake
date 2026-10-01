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
 * A thread-local mode in which a FROM relation's body is being computed: a derived table's, a view's, a CTE's
 * or a LATERAL body's rows, or the rows an EXISTS only counts. A select item whose value faults for a row
 * keeps the fault in its cell (see {@link DeferredFault}) instead of failing the statement, because live
 * computes such an item only where the reading statement reaches that row's value.
 *
 * <p>A query whose values are READ as they are produced - a scalar or IN subquery, a user-defined function's
 * body, a statement a block runs - suspends the mode while it runs, so its own items raise as they always did.
 */
public final class RelationBody {
    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<Boolean>();

    private RelationBody() {
    }

    /** Whether a relation body is being computed on this thread. */
    public static boolean isActive() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    /** Enter the mode; hands back the state to put back with {@link #end}. */
    public static boolean begin() {
        final boolean previous = isActive();
        ACTIVE.set(Boolean.TRUE);
        return previous;
    }

    /** Leave the mode for a query whose values are read as they are produced; put back with {@link #end}. */
    public static boolean suspend() {
        final boolean previous = isActive();
        ACTIVE.remove();
        return previous;
    }

    public static void end(final boolean previous) {
        if (previous) {
            ACTIVE.set(Boolean.TRUE);
        } else {
            ACTIVE.remove();
        }
    }
}
