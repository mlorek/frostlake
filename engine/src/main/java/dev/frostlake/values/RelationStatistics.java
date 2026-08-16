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

package dev.frostlake.values;

/**
 * The statistics a DERIVED relation — a subquery, a CTE, a view — still carries of the one catalog
 * table beneath it, so that a query over the relation is planned from them as a query over the table
 * would be. The account inlines such a relation: {@code SELECT SYSTEM$TYPEOF(COUNT(*)) FROM (SELECT *
 * FROM t)} is bounded by t's row count exactly as {@code FROM t} is.
 */
public interface RelationStatistics {

    /**
     * Whether every row of the table reaches the relation — through a projection, and a WHERE the
     * table's statistics prove true of every row. Decided on first use.
     *
     * @return true when the relation reads within the table's statistics
     */
    boolean readsWithinStatistics();

    /**
     * The table's row count.
     *
     * @return the count, or null when the table's rows are not reachable
     */
    Long sourceRowCount();

    /**
     * Whether the relation's column at {@code index} passes one of the table's stored columns through
     * unchanged — the kind of column a COUNT is answered from.
     *
     * @param index the column's position in the relation
     * @return true for a stored column passed through
     */
    boolean passesStoredColumn(int index);
}
