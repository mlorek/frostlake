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

import java.util.Map;

/**
 * The row around a grouped subquery while the aggregates of its groups are computed, which an aggregate's argument
 * reads the outer names through. The plan reads an outer number converted to the BOOLEAN its statistics settle as that
 * constant, and the row gives the same value: {@code (SELECT MAX(fz.id::BOOLEAN::INT + g.v) FROM g)} is answered for
 * each row of a table holding 5 and 7 (live-verified).
 *
 * <p>Scoped to the thread, like {@link RowOrdinal}. A grouping computed for no outer row clears it, so a query nested
 * in an aggregate's argument never reads the row around another.
 */
public final class AggregateOuterRow {

    private static final ThreadLocal<Map<String, Object>> CURRENT = new ThreadLocal<Map<String, Object>>();

    private AggregateOuterRow() {
    }

    /**
     * Note the outer row of the grouping about to compute its aggregates.
     *
     * @param outerRow the outer row's name to value bindings, or null for a grouping read for no outer row
     * @return the row it displaced, for {@link #end}
     */
    public static Map<String, Object> begin(final Map<String, Object> outerRow) {
        final Map<String, Object> previous = CURRENT.get();
        if (outerRow == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(outerRow);
        }
        return previous;
    }

    /** Put back whatever {@link #begin} displaced; null clears. */
    public static void end(final Map<String, Object> previous) {
        if (previous == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(previous);
        }
    }

    /** @return the outer row of the grouping computing its aggregates, or null */
    public static Map<String, Object> current() {
        return CURRENT.get();
    }
}
