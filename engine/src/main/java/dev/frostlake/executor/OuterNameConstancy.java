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

import dev.frostlake.values.ValueRange;

/**
 * Whether the statistics of the relation an outer name reads pin the name to one value on every row. Live's
 * planner reads such a name as that value, so a subquery reading it has nothing left to correlate: over
 * {@code h (c)} holding 2 in every row, {@code (SELECT g.v FROM g WHERE g.id = h.c + 3)} runs as
 * {@code (SELECT g.v FROM g WHERE g.id = 5)}, and a derived table's literal column, an aggregate the statistics
 * answer and a one-row table's column are read alike.
 */
public interface OuterNameConstancy {

    /**
     * Whether an outer name holds one value on every row.
     *
     * @param qualifier the relation the name is qualified by, upper-cased, or null for a bare name
     * @param column    the column, upper-cased
     * @return true where the statistics pin the name to one value that is never NULL
     */
    boolean holdsOneValue(String qualifier, String column);

    /**
     * Whether the statistics show an outer name taking more than one value, or a NULL beside a value. A catalog
     * table's column that does not hold one value does; a derived relation's column does only where it passes such
     * a column through (see {@link DerivedColumnLineage}), and one whose values the statistics do not reach — an
     * expression, a column of a relation the plan keeps whole — neither holds one value nor varies.
     *
     * @param qualifier the relation the name is qualified by, upper-cased, or null for a bare name
     * @param column    the column, upper-cased
     * @return true where the statistics show the name varying from row to row
     */
    default boolean variesAcrossRows(final String qualifier, final String column) {
        return !holdsOneValue(qualifier, column);
    }

    /**
     * Whether an interval is one known value that is never NULL, and one a condition may be settled by.
     *
     * @param range the interval, or null where none is known
     * @return true for a single point
     */
    static boolean pinsOneValue(final ValueRange range) {
        return range != null && !range.isEmpty() && !range.isNullable() && !range.isOpaqueToConditions()
            && range.getMin().compareTo(range.getMax()) == 0;
    }
}
