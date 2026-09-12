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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;

import java.util.List;
import java.util.Map;

/**
 * The value of an expression written over GROUPED rows, computed over the row's own source group.
 *
 * <p>A window stage that runs after grouping sees one row per group, and those rows no longer carry the
 * values an aggregate would need: {@code ROW_NUMBER() OVER (ORDER BY SUM(b))} has to know each group's
 * SUM(b), and {@code LAG(SUM(b)) OVER (…)} has to know it as an ARGUMENT. Where the aggregate is also a
 * SELECT item its computed value can be read positionally, but it need not be — and then the only place
 * left to compute it is the group the output row came from.
 *
 * <p>Implemented by the query executor, which holds the output-row-to-group map; the window evaluator
 * asks through this interface so it never has to know how grouping stored its rows.
 */
public interface GroupedExpressionValues {

    /** Answered when the expression cannot be computed over a group, so the caller keeps its own path. */
    Object UNRESOLVED = new Object();

    /**
     * The expression's value for one grouped output row.
     *
     * @param projectedRow   the output row, one per group
     * @param expressionText the expression as written
     * @return the value, or {@link #UNRESOLVED} when this row has no group or the text does not compute
     */
    Object valueOf(Row projectedRow, String expressionText);

    /**
     * The relation the groups were formed over — the FROM table the projected rows no longer are.
     *
     * <p>A window stage over grouped rows resolves VALUES against the projected shape, but a DECLARED
     * type is a property of the base relation: {@code RATIO_TO_REPORT(SUM(a)) OVER ()} beside
     * {@code GROUP BY a} is NUMBER(30,8) on the account because {@code a} is the NUMBER(10,2) column it
     * always was, not the untyped slot the projection put it in. This is where that relation is found.
     *
     * @return the base table, never null
     */
    Table baseTable();

    /**
     * The FROM clause's alias map paired with {@link #baseTable()}, or null for a single relation.
     *
     * @return alias to table, or null
     */
    Map<String, Table> aliasToTable();

    /**
     * Every joined relation paired with {@link #baseTable()}, or null for a single relation.
     *
     * @return the joined tables, or null
     */
    List<Table> allTables();
}
