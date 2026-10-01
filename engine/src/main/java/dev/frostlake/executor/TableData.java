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

import dev.frostlake.executor.operators.MemoizedRows;
import dev.frostlake.executor.operators.Operator;
import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.executor.operators.SourceOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * A FROM source as the planner holds it: its shape — the relation's columns, and the alias it answers to —
 * settled while planning, and its rows, either in hand (a VALUES clause, a CTE the WITH clause computed) or
 * produced by the source stage when the pipeline runs. The rows list produces them when first touched, so a
 * plan that needs them early reads them early, and the plan's description says which sources were read while
 * planning.
 */
class TableData {
    Table table;
    List<Row> rows;
    String alias;
    /** The rows as a stage reads them: produced once, when first asked. */
    final MemoizedRows source;
    private String description;
    private boolean readWhilePlanning;

    /**
     * A relation whose rows are in hand.
     *
     * @param table the relation's shape
     * @param rows  its rows
     * @param alias the alias it answers to, or null
     */
    TableData(final Table table, final List<Row> rows, final String alias) {
        this.table = table;
        this.rows = rows;
        this.alias = alias;
        this.source = new MemoizedRows(described(RowsProvider.of(rows)));
    }

    /**
     * A relation whose rows are produced when the source stage runs.
     *
     * @param table       the relation's shape
     * @param rows        the read producing its rows
     * @param alias       the alias it answers to, or null
     * @param description how the read reads in a plan
     */
    TableData(final Table table, final RowsProvider rows, final String alias, final String description) {
        this.table = table;
        this.alias = alias;
        this.description = description;
        this.source = new MemoizedRows(described(rows));
        this.rows = new DeferredRows(source);
    }

    private TableData(final Table table, final List<Row> rows, final String alias, final MemoizedRows source,
                      final String description, final boolean readWhilePlanning) {
        this.table = table;
        this.rows = rows;
        this.alias = alias;
        this.source = source;
        this.description = description;
        this.readWhilePlanning = readWhilePlanning;
    }

    private RowsProvider described(final RowsProvider rows) {
        return new RowsProvider() {
            @Override
            public List<Row> rows() {
                return rows.rows();
            }

            @Override
            public String describe() {
                return TableData.this.describe();
            }
        };
    }

    /**
     * Names how the source reads in a plan.
     *
     * @param text the description
     * @return this source
     */
    TableData describedAs(final String text) {
        this.description = text;
        return this;
    }

    /**
     * Marks the source as read while planning — its rows were needed to know its shape, or the read has no
     * deferred form yet.
     *
     * @return this source
     */
    TableData readWhilePlanning() {
        this.readWhilePlanning = true;
        return this;
    }

    /** How the source reads in a plan; a source read while planning is marked. */
    String describe() {
        return (description != null ? description : "SOURCE[" + table.getName() + "]") + (readWhilePlanning ? "*" : "");
    }

    /**
     * The same rows under a renamed shape.
     *
     * @param renamed the shape
     * @return the source
     */
    TableData withTable(final Table renamed) {
        return new TableData(renamed, rows, alias, source, description, readWhilePlanning);
    }

    /** The source's shape with no row, as a relation read for its shape only answers. */
    TableData withoutRows() {
        final TableData shape = new TableData(table, new ArrayList<Row>(), alias);
        shape.description = description;
        shape.readWhilePlanning = readWhilePlanning;
        return shape;
    }

    /** The stage that answers the source's rows, the first of a plan over it. */
    Operator sourceStage() {
        return new SourceOperator(source, describe(), false);
    }
}
