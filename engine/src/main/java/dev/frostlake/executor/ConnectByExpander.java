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

import dev.frostlake.executor.expressions.ConnectByRootExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.PriorExpression;
import dev.frostlake.executor.expressions.SqlTruth;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.NumericType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Snowflake hierarchical queries — {@code [START WITH <pred>] CONNECT BY [PRIOR] c = [PRIOR] c}.
 *
 * <p>The expansion walks the FROM clause's rows as a forest: the rows matching START WITH are the roots
 * (without START WITH every row is its own root, live-verified), and a row becomes a child of the row
 * above it whenever the CONNECT BY predicate holds with {@code PRIOR} bound to the parent and every other
 * reference bound to the candidate child. Each emitted row carries its own values plus two pseudo-column
 * groups, both hidden from {@code SELECT *} exactly as Snowflake hides them:
 * <ul>
 *   <li>{@code LEVEL} — 1 for a root, +1 per step down;</li>
 *   <li>{@code CONNECT_BY_ROOT$<col>} — the root row's value of every source column, which is what
 *       {@link ConnectByRootExpression} reads.</li>
 * </ul>
 *
 * <p>Snowflake applies WHERE to the EXPANDED rows (live-verified: filtering out a parent still keeps its
 * children), so this runs before the WHERE stage and everything downstream sees an ordinary relation.
 *
 * <p>Snowflake performs no cycle detection — a cyclic hierarchy simply runs until the warehouse times out
 * (live-verified). An in-memory engine cannot hang, so the walk is bounded by {@link #MAX_DEPTH}, the same
 * guard value the recursive-CTE fixpoint loop uses, and reports the cycle instead.
 */
public final class ConnectByExpander {

    /** Maximum hierarchy depth before the walk is reported as cyclic. */
    public static final int MAX_DEPTH = 1000;

    /** The {@code LEVEL} pseudo-column's name. */
    public static final String LEVEL_COLUMN = "LEVEL";

    private final QueryExecutor executor;
    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;

    public ConnectByExpander(final QueryExecutor executor, final FunctionRegistry functionRegistry,
                             final Catalog catalog) {
        this.executor = executor;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
    }

    /** The out-of-the-way name a source column called {@code LEVEL} takes in the expanded relation,
     *  so the name {@code LEVEL} resolves to the depth pseudo-column. */
    public static final String SHADOWED_LEVEL_COLUMN = "LEVEL$SHADOWED";

    /**
     * The relation the expansion produces: the source columns followed by the {@code LEVEL} depth
     * pseudo-column and the hidden {@code CONNECT_BY_ROOT$<col>} pseudo-columns. The pseudo-column
     * SHADOWS a source column of the same name — live, both the
     * bare {@code level} and the QUALIFIED {@code t.level} answer the depth inside a CONNECT BY
     * query, even over a table whose own {@code LEVEL} column holds other values — so a clashing
     * source column is renamed out of resolution here. Without a clash the depth column stays hidden
     * from {@code SELECT *}, exactly as before; with one it takes the source column's star slot.
     */
    public Table expandedTable(final Table baseTable) {
        final boolean clash = hasColumnNamed(baseTable, LEVEL_COLUMN);
        final List<TableColumn> columns = new ArrayList<>();
        for (final TableColumn column : baseTable.getColumns()) {
            if (clash && column.getName().equalsIgnoreCase(LEVEL_COLUMN)) {
                columns.add(new TableColumn(SHADOWED_LEVEL_COLUMN, column.getDataType(),
                    true, null, false, false, false).starHiddenCopy());
            } else {
                columns.add(column);
            }
        }
        final TableColumn depth =
            new TableColumn(LEVEL_COLUMN, NumericType.NUMBER, true, null, false, false, false);
        columns.add(clash ? depth : depth.starHiddenCopy());
        for (final TableColumn column : baseTable.getColumns()) {
            columns.add(new TableColumn(ConnectByRootExpression.ROOT_PREFIX + column.getName(),
                column.getDataType(), true, null, false, false, false).starHiddenCopy());
        }
        return new Table(baseTable.getName(), columns, false);
    }

    /**
     * Expand {@code baseRows} into the hierarchy described by the clause, in depth-first pre-order
     * (parent immediately before its subtree). The result's rows match {@link #expandedTable}'s shape.
     */
    public List<Row> expand(final List<Row> baseRows, final Table baseTable, final Table expandedTable,
                            final FrostlakeParser.ConnectByClauseContext clause,
                            final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final int width = baseTable.getColumns().size();

        final boolean[] isSeed = new boolean[baseRows.size()];
        if (clause.startWithClause() != null) {
            final Expression seedPredicate =
                ExpressionEvaluator.parse(executor.getOriginalText(clause.startWithClause().booleanExpr()));
            final ExpressionEvaluator seedEvaluator =
                new ExpressionEvaluator(baseTable, functionRegistry, catalog, executor);
            seedEvaluator.setMultiTableContext(aliasToTable, allTables);
            for (int i = 0; i < baseRows.size(); i++) {
                isSeed[i] = SqlTruth.isTrue(seedEvaluator.evaluate(seedPredicate, baseRows.get(i)));
            }
        } else {
            // No START WITH: every row seeds its own tree (live-verified).
            for (int i = 0; i < isSeed.length; i++) {
                isSeed[i] = true;
            }
        }

        // The CONNECT BY predicate reads two rows at once, so it is evaluated against a synthetic relation
        // whose columns are the candidate child's followed by the parent's under PRIOR$ names — which is
        // exactly what PriorExpression resolves to.
        final Table stepTable = stepTable(baseTable);
        final Expression stepPredicate =
            ExpressionEvaluator.parse(executor.getOriginalText(clause.connectByPredicate().booleanExpr()));
        final ExpressionEvaluator stepEvaluator =
            new ExpressionEvaluator(stepTable, functionRegistry, catalog, executor);

        final List<Row> result = new ArrayList<>();
        final Deque<int[]> pending = new ArrayDeque<>();
        for (int i = baseRows.size() - 1; i >= 0; i--) {
            if (isSeed[i]) {
                pending.push(new int[]{i, 1, i});
            }
        }
        while (!pending.isEmpty()) {
            final int[] step = pending.pop();
            final Row row = baseRows.get(step[0]);
            final int level = step[1];
            final Row rootRow = baseRows.get(step[2]);
            if (level > MAX_DEPTH) {
                throw new RuntimeException("CONNECT BY exceeded the maximum hierarchy depth of "
                    + MAX_DEPTH + " — the data most likely contains a cycle.");
            }
            final List<Object> values = new ArrayList<>(row.getValues());
            values.add(Long.valueOf(level));
            for (int c = 0; c < width; c++) {
                values.add(c < rootRow.getValues().size() ? rootRow.getValue(c) : null);
            }
            result.add(new Row(values));

            // Children are pushed in reverse so the walk emits them in source order.
            for (int i = baseRows.size() - 1; i >= 0; i--) {
                final List<Object> combined = new ArrayList<>(baseRows.get(i).getValues());
                combined.addAll(row.getValues());
                if (SqlTruth.isTrue(stepEvaluator.evaluate(stepPredicate, new Row(combined)))) {
                    pending.push(new int[]{i, level + 1, step[2]});
                }
            }
        }
        return result;
    }

    /** The candidate-plus-parent relation the CONNECT BY predicate is evaluated against. */
    private Table stepTable(final Table baseTable) {
        final List<TableColumn> columns = new ArrayList<>(baseTable.getColumns());
        for (final TableColumn column : baseTable.getColumns()) {
            columns.add(new TableColumn(PriorExpression.PRIOR_PREFIX + column.getName(),
                column.getDataType(), true, null, false, false, false));
        }
        return new Table(baseTable.getName(), columns, false);
    }

    private static boolean hasColumnNamed(final Table table, final String name) {
        for (final TableColumn column : table.getColumns()) {
            if (column.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
