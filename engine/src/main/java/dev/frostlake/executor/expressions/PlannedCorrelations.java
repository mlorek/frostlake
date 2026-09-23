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

import java.util.Set;

/**
 * A thread-local record of the correlated subqueries a statement's plan has already refused (see
 * {@code CorrelationPlanJudgement}). The refusal waits until the rest of the statement has been judged, as
 * live reports it after a predicate's type or a grouped select list; meanwhile a row that reaches such a
 * subquery reads it as empty rather than running a shape the statement will not keep.
 */
public final class PlannedCorrelations {
    private static final ThreadLocal<Set<String>> REFUSED = new ThreadLocal<Set<String>>();

    private PlannedCorrelations() {
    }

    /**
     * Record the subqueries refused for the statement about to run.
     *
     * @param refused the refused subqueries, each keyed by {@link #valueKey} or {@link #membershipKey}; null
     *                clears the record
     * @return the record displaced, to put back with {@link #end}
     */
    public static Set<String> begin(final Set<String> refused) {
        final Set<String> previous = REFUSED.get();
        if (refused == null) {
            REFUSED.remove();
        } else {
            REFUSED.set(refused);
        }
        return previous;
    }

    public static void end(final Set<String> previous) {
        begin(previous);
    }

    /** The key of a subquery read as a value. */
    public static String valueKey(final String subquery) {
        return "value " + subquery;
    }

    /** The key of a subquery under EXISTS or IN. */
    public static String membershipKey(final String subquery) {
        return "membership " + subquery;
    }

    /** Whether the running statement's plan refused the subquery under {@code key}. */
    static boolean refused(final String key) {
        final Set<String> refused = REFUSED.get();
        return refused != null && refused.contains(key);
    }
}
