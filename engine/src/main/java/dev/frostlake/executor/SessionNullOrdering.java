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
 * Where a sort puts its NULLs when its key names no NULLS FIRST or NULLS LAST: as the session's DEFAULT_NULL_ORDERING
 * says, read without regard to case (live-verified).
 *
 * <pre>
 *   LAST (the default)   NULLs after every value in an ascending sort, before every value in a descending one
 *   FIRST                NULLs before every value in an ascending sort, after every value in a descending one
 * </pre>
 *
 * <p>Every sort a statement runs follows it — a query's ORDER BY with its LIMIT and FETCH, a window's ORDER BY and so
 * its ranks and frames, WITHIN GROUP, MATCH_RECOGNIZE, a dynamic PIVOT's columns — and so does every sort key the
 * account prints, in EXPLAIN and in a refusal: {@code ROW_NUMBER() OVER (ORDER BY T.A ASC NULLS FIRST)}. A JSON null
 * is a value, and keeps its place among the values.
 *
 * <p>Held in a ThreadLocal pinned for the statement, as {@link SessionWeek} carries the week, for the same reason:
 * one engine serves many sessions and a comparator has no session to ask.
 */
public final class SessionNullOrdering {

    private static final ThreadLocal<Boolean> PINNED = new ThreadLocal<Boolean>();

    private SessionNullOrdering() {
    }

    /**
     * Pin this statement's placement, handing back what was pinned before so the caller can restore it. Any value
     * but FIRST is the default.
     *
     * @param value the session's DEFAULT_NULL_ORDERING, or null
     * @return what was pinned before
     */
    public static Boolean pin(final Object value) {
        final Boolean previous = PINNED.get();
        PINNED.set(Boolean.valueOf(value != null && "FIRST".equalsIgnoreCase(value.toString().trim())));
        return previous;
    }

    /**
     * Put back what {@link #pin} displaced.
     *
     * @param previous what it answered
     */
    public static void restore(final Boolean previous) {
        if (previous == null) {
            PINNED.remove();
        } else {
            PINNED.set(previous);
        }
    }

    /**
     * Whether a sort key's NULLs come first: as its NULLS clause says, else as the session's placement puts them for
     * the key's direction.
     *
     * @param ascending whether the key sorts ascending
     * @param written   NULLS FIRST (TRUE) or NULLS LAST (FALSE) as written, or null for neither
     * @return whether its NULLs sort first
     */
    public static boolean nullsFirst(final boolean ascending, final Boolean written) {
        if (written != null) {
            return written.booleanValue();
        }
        return Boolean.TRUE.equals(PINNED.get()) ? ascending : !ascending;
    }
}
