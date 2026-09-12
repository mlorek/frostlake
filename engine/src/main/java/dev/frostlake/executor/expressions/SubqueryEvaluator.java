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

import dev.frostlake.executor.CorrelatedSubqueryRule;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** The subqueries whose correlation this evaluator has judged supported, keyed by use and text. */
    private final Set<String> judgedCorrelations = new HashSet<>();

    SubqueryEvaluator(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    Object evaluateExists(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXISTS: QueryExecutor not available");
        }

        requireSupportedCorrelation(expr, false);
        final List<ResultSet> results = executeSubquery(expr.getSubquery());

        if (results.isEmpty()) {
            return false;
        }

        return results.get(0).getRowCount() > 0;
    }

    Object evaluateScalarSubquery(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate subquery: QueryExecutor not available");
        }

        requireSupportedCorrelation(expr, true);
        final List<ResultSet> results = executeSubquery(expr.getSubquery());

        // More than one column is refused before any row is counted: a set operation's rows must not
        // surface as the single-row fault, and a single row must not answer with its first column.
        if (!results.isEmpty() && results.get(0) != null && results.get(0).getColumns().size() > 1) {
            throw visitor.multiColumnRefusal(expr);
        }
        if (results.isEmpty() || results.get(0).getRowCount() == 0) {
            return null;
        }

        final ResultSet resultSet = results.get(0);
        if (resultSet.getRowCount() > 1) {
            // Live's own sentence, full stop included.
            throw new RuntimeException("Single-row subquery returns more than one row.");
        }

        return resultSet.getRows().get(0).getValue(0);
    }

    Object evaluateInSubquery(final Object value, final SubqueryExpression expr, final boolean not,
                              final CollationSpec collation) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate IN subquery: QueryExecutor not available");
        }

        final String subquery = expr.getSubquery();
        requireSupportedCorrelation(expr, false);
        final List<ResultSet> results = executeSubquery(subquery);

        if (results.isEmpty()) {
            return not;
        }

        final ResultSet resultSet = results.get(0);

        // Three-valued logic (live-verified), mirroring the literal-list IN in
        // ExpressionEvaluatorVisitor.visitIn: a NULL probe is UNKNOWN even over an EMPTY subquery
        // (the probe short-circuits before membership is considered); a non-NULL probe over an
        // empty subquery is FALSE for IN / TRUE for NOT IN; and a miss over a set that contains a
        // NULL member is UNKNOWN for both.
        if (value == null) {
            return null;
        }
        if (resultSet.getRowCount() == 0) {
            return not;
        }

        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        // Uncorrelated subquery (result cached, identical for every outer row): build a membership
        // index once and probe O(1) per outer row, instead of an O(subqueryRows) linear scan per row.
        // A collated probe cannot use the hash index: membership is what the collation calls equal,
        // not what hashes alike, so the scan below settles it.
        if (collation == null && subqueryMemo != null && subqueryMemo.cachedResult(subquery) != null) {
            PreparedInSet set = subqueryMemo.cachedInSet(subquery);
            if (set == null) {
                set = PreparedInSet.build(resultSet.getRows());
                subqueryMemo.recordInSet(subquery, set);
            }
            if (set.contains(value)) {
                return !not;
            }
            return set.hasNull() ? null : not;
        }

        // Correlated (or no memo): linear scan with early exit (result differs per outer row).
        boolean anyNull = false;
        for (final Row subRow : resultSet.getRows()) {
            final Object subValue = subRow.getValue(0);
            if (subValue == null) {
                anyNull = true;
                continue;
            }
            if (collation != null ? CollationMatching.equalUnder(collation, value, subValue)
                    : ExpressionArithmetic.equals(value, subValue)) {
                return !not;
            }
        }

        return anyNull ? null : not;
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
            final List<ResultSet> cached = subqueryMemo.cachedResult(subquery);
            if (cached != null) {
                return cached;
            }
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final Map<String, Object> context = buildLateralContext();
        if (subqueryMemo == null || subqueryMemo.isCorrelated(subquery)) {
            return queryExecutor.executeWithLateralContext(subquery, context);
        }
        final long before = visitor.lateralReadCount();
        final List<ResultSet> results = queryExecutor.executeWithLateralContext(subquery, context);
        if (visitor.lateralReadCount() == before) {
            subqueryMemo.recordUncorrelated(subquery, results);
        } else {
            subqueryMemo.recordCorrelated(subquery);
        }
        return results;
    }

    /**
     * Refuse a correlated subquery whose shape live cannot evaluate (see {@link CorrelatedSubqueryRule}) the
     * first time a row reaches it, so an outer query that reads no row never meets the refusal, as live's
     * never does. Each subquery is judged once per use for this evaluator. The refusal is positioned at
     * the subquery's SELECT, or at the EXISTS keyword of an EXISTS.
     */
    private void requireSupportedCorrelation(final SubqueryExpression expr, final boolean scalar) {
        final String key = (scalar ? "value " : "membership ") + expr.getSubquery();
        if (judgedCorrelations.contains(key)) {
            return;
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null && !outerHoldsOneRowAtMost(queryExecutor)) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase());
            }
            final CorrelatedSubqueryRule rule = new CorrelatedSubqueryRule(queryExecutor, outerNames);
            if (scalar ? rule.refusesScalar(parsed) : rule.refusesMembership(parsed)) {
                final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
                throw new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"
                    + (at == null ? "" : " at line " + at.getLine() + ", position " + at.getCharPositionInLine())));
            }
        }
        judgedCorrelations.add(key);
    }

    /**
     * Whether every catalog table the evaluated row is drawn from holds at most one row. Live evaluates a
     * correlated subquery row by row over such an outer relation instead of planning it as a join, so no
     * shape is refused there, and a second inner row surfaces as the single-row fault instead. A table
     * function beside the table does not count.
     */
    private boolean outerHoldsOneRowAtMost(final QueryExecutor queryExecutor) {
        final List<Table> relations = new ArrayList<>();
        if (visitor.getMultiTableAllTables() != null && !visitor.getMultiTableAllTables().isEmpty()) {
            relations.addAll(visitor.getMultiTableAllTables());
        } else if (visitor.getTable() != null) {
            relations.add(visitor.getTable());
        }
        boolean sawCatalogTable = false;
        for (final Table relation : relations) {
            if (!relation.isCatalogResident()) {
                continue;
            }
            final long rows = queryExecutor.storedRowCount(relation.getQualifiedName());
            if (rows < 0 || rows > 1) {
                return false;
            }
            sawCatalogTable = true;
        }
        return sawCatalogTable;
    }

    /**
     * The outer names a correlated subquery may read, each bound to NULL: the scope its SHAPE is planned
     * in before any outer row exists. The keys are the ones {@link #buildLateralContext} binds per row.
     */
    Map<String, Object> outerNamesContext() {
        final Map<String, Object> context = new HashMap<>();
        final Map<String, Object> lateralContext = visitor.getLateralContext();
        if (lateralContext != null) {
            context.putAll(lateralContext);
        }
        final Table table = visitor.getTable();
        if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                final String colName = column.getName();
                context.put(table.getName() + "." + colName, null);
                context.put(table.getName().toUpperCase() + "." + colName.toUpperCase(), null);
                context.put(colName, null);
                context.put(colName.toUpperCase(), null);
            }
        }
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                for (final TableColumn column : entry.getValue().getColumns()) {
                    context.put((entry.getKey() + "." + column.getName()).toUpperCase(), null);
                }
            }
        }
        return context;
    }

    private Map<String, Object> buildLateralContext() {
        final Map<String, Object> context = new HashMap<>();

        final Map<String, Object> lateralContext = visitor.getLateralContext();
        if (lateralContext != null) {
            context.putAll(lateralContext);
        }

        final Table table = visitor.getTable();
        final Row row = visitor.getRow();
        if (table != null && row != null && !visitor.isScopeOpaqueToSubqueries()) {
            final List<TableColumn> columns = table.getColumns();
            for (int i = 0; i < columns.size(); i++) {
                final String colName = columns.get(i).getName();
                final Object value = row.getValue(i);
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
