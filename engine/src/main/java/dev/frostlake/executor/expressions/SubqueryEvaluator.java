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

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Subquery / correlated-subquery evaluation extracted from {@link ExpressionEvaluatorVisitor}: EXISTS,
 * scalar and IN subqueries, the uncorrelated-result memoization in {@link #executeSubquery(String)}, and
 * the lateral-context assembly for the outer row.
 *
 * <p>All of the mutable per-row state this needs — the current {@code row}, the (settable) lateral and
 * subquery-memo context, and the query executor — lives on the owning visitor and changes as evaluation
 * walks each outer row, so this holds only a back-reference and reads every value live through the
 * visitor's package-private accessors rather than snapshotting it.
 */
final class SubqueryEvaluator {

    private final ExpressionEvaluatorVisitor visitor;

    SubqueryEvaluator(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    Object evaluateExists(final String subquery) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXISTS: QueryExecutor not available");
        }

        List<ResultSet> results = executeSubquery(subquery);

        if (results.isEmpty()) {
            return false;
        }

        return results.get(0).getRowCount() > 0;
    }

    Object evaluateScalarSubquery(final String subquery) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate subquery: QueryExecutor not available");
        }

        List<ResultSet> results = executeSubquery(subquery);

        if (results.isEmpty() || results.get(0).getRowCount() == 0) {
            return null;
        }

        ResultSet resultSet = results.get(0);
        if (resultSet.getRowCount() > 1) {
            throw new RuntimeException("Scalar subquery returned more than one row");
        }

        return resultSet.getRows().get(0).getValue(0);
    }

    Object evaluateInSubquery(final Object value, final String subquery, final boolean not) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate IN subquery: QueryExecutor not available");
        }

        List<ResultSet> results = executeSubquery(subquery);

        if (results.isEmpty()) {
            return not;
        }

        ResultSet resultSet = results.get(0);

        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        // Uncorrelated subquery (result cached, identical for every outer row): build a membership
        // index once and probe O(1) per outer row, instead of an O(subqueryRows) linear scan per row.
        if (subqueryMemo != null && subqueryMemo.cachedResult(subquery) != null) {
            PreparedInSet set = subqueryMemo.cachedInSet(subquery);
            if (set == null) {
                set = PreparedInSet.build(resultSet.getRows());
                subqueryMemo.recordInSet(subquery, set);
            }
            return set.contains(value) ? !not : not;
        }

        // Correlated (or no memo): linear scan with early exit (result differs per outer row).
        for (final Row subRow : resultSet.getRows()) {
            Object subValue = subRow.getValue(0);
            if (ExpressionArithmetic.equals(value, subValue)) {
                return !not;
            }
        }

        return not;
    }

    /**
     * Execute a subquery, memoizing the result of uncorrelated subqueries (those that read no outer
     * value during execution) so they run once per outer query instead of once per outer row. The
     * first execution probes the per-thread lateral-read counter; a zero delta proves the subquery is
     * uncorrelated and its result reusable. Correlated subqueries always re-execute. With no memo
     * configured this is exactly the previous behaviour (build context, execute).
     */
    List<ResultSet> executeSubquery(final String subquery) {
        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        if (subqueryMemo != null) {
            List<ResultSet> cached = subqueryMemo.cachedResult(subquery);
            if (cached != null) {
                return cached;
            }
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        Map<String, Object> context = buildLateralContext();
        if (subqueryMemo == null || subqueryMemo.isCorrelated(subquery)) {
            return queryExecutor.executeWithLateralContext(subquery, context);
        }
        long before = visitor.lateralReadCount();
        List<ResultSet> results = queryExecutor.executeWithLateralContext(subquery, context);
        if (visitor.lateralReadCount() == before) {
            subqueryMemo.recordUncorrelated(subquery, results);
        } else {
            subqueryMemo.recordCorrelated(subquery);
        }
        return results;
    }

    private Map<String, Object> buildLateralContext() {
        Map<String, Object> context = new HashMap<>();

        final Map<String, Object> lateralContext = visitor.getLateralContext();
        if (lateralContext != null) {
            context.putAll(lateralContext);
        }

        final Table table = visitor.getTable();
        final Row row = visitor.getRow();
        if (table != null && row != null) {
            List<TableColumn> columns = table.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i).getName();
                Object value = row.getValue(i);
                context.put(table.getName() + "." + colName, value);
                context.put(table.getName().toUpperCase() + "." + colName.toUpperCase(), value);
                // BARE names too: a correlated subquery may reference an outer column unqualified —
                // notably FLATTEN outputs (WHERE EXISTS (... WHERE k = VALUE:field)), which have no
                // natural alias. The subquery's own columns still win: this context is only consulted
                // after resolution against the inner tables has failed.
                context.put(colName, value);
                context.put(colName.toUpperCase(), value);
            }
        }

        // ALIAS-qualified keys: a correlated subquery references the outer row by its FROM alias
        // (WHERE t.id = s.id), which is not the table's name. Only TABLENAME.col keys were assembled, so the
        // alias-qualified lookup missed and the strip-qualifier fallback bound the reference to the INNER
        // table's same-named column — turning the correlation into t.id = t.id (always true): EXISTS matched
        // every outer row and NOT EXISTS none. Values are read positionally; the combined row lays the
        // tables out in allTables order, exactly as the executor's lateral join assembles it.
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        final List<Table> allTables = visitor.getMultiTableAllTables();
        if (aliasToTable != null && allTables != null && row != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                int offset = 0;
                for (final Table t : allTables) {
                    if (t == entry.getValue()) {
                        break;
                    }
                    offset += t.getColumns().size();
                }
                final Table aliased = entry.getValue();
                for (int i = 0; i < aliased.getColumns().size() && offset + i < row.getValues().size(); i++) {
                    context.put((entry.getKey() + "." + aliased.getColumns().get(i).getName()).toUpperCase(),
                        row.getValue(offset + i));
                }
            }
        }

        return context;
    }
}
