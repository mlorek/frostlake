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
import dev.frostlake.executor.DeferredFault;
import dev.frostlake.executor.DerivedColumnLineage;
import dev.frostlake.executor.EqualityCorrelation;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.OuterNameConstancy;
import dev.frostlake.executor.ProjectionLineage;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RelationBody;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StoredColumnValues;
import dev.frostlake.executor.SubqueryAhead;
import dev.frostlake.executor.SubqueryCompilation;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.JoinedRelations;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.values.ValueRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    /** Each EXISTS met, by its copy's key, as it is run (see {@link #countedBody}). */
    private final Map<String, SubqueryExpression> countedBodies = new HashMap<>();
    /** Scalar subqueries by key, each as it is run (see {@link #statisticsBody}). */
    private final Map<String, SubqueryExpression> statisticsBodies = new HashMap<>();
    /** Each copy's equality correlation, by key, as analyzed once (see {@link #equalityCorrelationOf}). */
    private final Map<String, EqualityCorrelation> equalityCorrelations = new HashMap<>();
    /** The outer keys of each copy's equality correlation, parsed, by key. */
    private final Map<String, List<Expression>> outerKeys = new HashMap<>();
    /** The copies run for each row: no equality correlation, or one that could not be computed once for all. */
    private final Set<String> perRowOnly = new HashSet<>();

    SubqueryEvaluator(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    Object evaluateExists(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate EXISTS: QueryExecutor not available");
        }

        final SubqueryExpression body = countedBody(expr, null);
        if (requireSupportedCorrelation(expr, body, false, false, false, null, expr.getPosition())) {
            return false;
        }
        final Boolean probed = probedExists(expr, body);
        if (probed != null) {
            return probed;
        }
        final List<ResultSet> results = countedSubquery(body);

        if (results.isEmpty()) {
            return false;
        }

        return results.get(0).getRowCount() > 0;
    }

    /**
     * An EXISTS's body as it is run: an aggregate of its select list over the outer row alone is the outer
     * query's value, so the body counts its rows with a constant in its place (see
     * {@link CorrelatedSubqueryRule#countedBodyText}). Every other subquery is run as written.
     *
     * @param expr  the subquery
     * @param names the names the scope offers the subquery where it is compiling, or null for the row's own
     * @return the subquery to run, positioned as {@code expr} is
     */
    private SubqueryExpression countedBody(final SubqueryExpression expr, final Map<String, Object> names) {
        if (!expr.isExistence()) {
            return expr;
        }
        final String key = memoKey(expr);
        SubqueryExpression body = names == null ? countedBodies.get(key) : null;
        if (body != null) {
            return body;
        }
        body = expr;
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : (names == null ? buildLateralContext() : names).keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
                .countedBodyText(parsed);
            if (text != null) {
                body = new SubqueryExpression(text);
                body.setPosition(expr.getPosition());
                body.setQueryPosition(expr.getQueryPosition());
                body.setExistence(true);
            }
        }
        if (names == null) {
            countedBodies.put(key, body);
        }
        return body;
    }

    /**
     * A subquery other than an EXISTS as it is compiled: an aggregate over the outer row alone in a derived table's
     * select list written as the value it runs with (see {@link CorrelatedSubqueryRule#derivedBodyText}), so the
     * derived table is planned with no aggregate of its own. Every other subquery is compiled as written.
     *
     * @param expr  the subquery
     * @param names the names the scope offers the subquery where it is compiling
     * @return the subquery to compile, positioned as {@code expr} is
     */
    private SubqueryExpression derivedBody(final SubqueryExpression expr, final Map<String, Object> names) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed == null) {
            return expr;
        }
        final Set<String> outerNames = new HashSet<>();
        for (final String name : names.keySet()) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
            .derivedBodyText(parsed);
        if (text == null) {
            return expr;
        }
        final SubqueryExpression body = new SubqueryExpression(text);
        body.setPosition(expr.getPosition());
        body.setQueryPosition(expr.getQueryPosition());
        return body;
    }

    /**
     * A scalar subquery as it is run: an aggregate of its select list that the outer table's statistics answer is
     * a value, written in its place (see {@link CorrelatedSubqueryRule#statisticsBodyText}). Every other subquery
     * is run as written.
     *
     * @param expr the subquery
     * @return the subquery to run, positioned as {@code expr} is
     */
    private SubqueryExpression statisticsBody(final SubqueryExpression expr) {
        final String key = memoKey(expr);
        SubqueryExpression body = statisticsBodies.get(key);
        if (body != null) {
            return body;
        }
        body = expr;
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final String text = new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations())
                .statisticsBodyText(parsed);
            if (text != null) {
                body = new SubqueryExpression(text);
                body.setPosition(expr.getPosition());
                body.setQueryPosition(expr.getQueryPosition());
            }
        }
        statisticsBodies.put(key, body);
        return body;
    }

    Object evaluateScalarSubquery(final SubqueryExpression expr) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate subquery: QueryExecutor not available");
        }

        if (requireSupportedCorrelation(expr, true, false, false, null)) {
            return null;
        }
        // An aggregate over an equality correlation reads its value by the outer row's key from values computed once
        // for every key; a row no key equals runs the subquery once, and its value is the one every such row reads.
        final PreparedKeyedValues keyed = keyedValuesOf(expr);
        boolean missed = false;
        if (keyed != null) {
            final List<Object> probe = outerKeyValues(memoKey(expr));
            if (probe != null && (holdsNull(probe) || keyed.answers(probe))) {
                if (!holdsNull(probe) && keyed.holds(probe)) {
                    final Object cell = keyed.valueOf(probe);
                    if (!(cell instanceof DeferredFault)) {
                        return cell;
                    }
                } else if (keyed.knowsEmptyValue()) {
                    return keyed.emptyValue();
                } else {
                    missed = true;
                }
            }
        }
        final Object value = scalarSubqueryValue(expr);
        if (missed) {
            keyed.recordEmptyValue(value);
        }
        return value;
    }

    /**
     * An EXISTS over an equality correlation (see {@link EqualityCorrelation}), answered by probing its inner keys,
     * computed once for the statement, with the outer row's key: the answer running the subquery for the row gives,
     * in time that grows with the two relations rather than with their product. Null where the probe cannot settle
     * it — no such correlation, a key of a kind hashing does not compare, a collation — and the subquery runs.
     */
    private Boolean probedExists(final SubqueryExpression expr, final SubqueryExpression body) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final String key = memoKey(expr);
        final EqualityCorrelation correlation = memo == null ? null : equalityCorrelationOf(key, body);
        if (correlation == null || !correlation.answersARowPerKeptRow()) {
            return null;
        }
        final String keysQuery = correlation.keysQuery();
        PreparedKeySet keys = memo.cachedKeySet(keysQuery);
        if (keys == null) {
            final ResultSet computed = computedOnce(key, keysQuery, correlation.keyCount());
            if (computed == null) {
                return null;
            }
            keys = PreparedKeySet.build(computed.getRows(), correlation.keyCount());
            memo.recordKeySet(keysQuery, keys);
        }
        final List<Object> probe = outerKeyValues(key);
        if (probe == null) {
            return null;
        }
        if (holdsNull(probe)) {
            return Boolean.FALSE;
        }
        return keys.answers(probe) ? Boolean.valueOf(keys.contains(probe)) : null;
    }

    /**
     * The values by key of a scalar aggregate over an equality correlation, computed once for the statement, or null
     * where the subquery runs for each row.
     */
    private PreparedKeyedValues keyedValuesOf(final SubqueryExpression expr) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final String key = memoKey(expr);
        if (memo == null || perRowOnly.contains(key) || statisticsBody(expr) != expr) {
            return null;
        }
        final EqualityCorrelation correlation = equalityCorrelationOf(key, expr);
        if (correlation == null || correlation.getAggregateItem() == null) {
            perRowOnly.add(key);
            return null;
        }
        final String groupedQuery = correlation.groupedQuery();
        PreparedKeyedValues values = memo.cachedKeyedValues(groupedQuery);
        if (values == null) {
            final ResultSet computed = computedOnce(key, groupedQuery, correlation.keyCount() + 1);
            if (computed == null) {
                return null;
            }
            final ResultSetColumn valueColumn = computed.getColumns().get(correlation.keyCount());
            if (valueColumn.isUncheckedConstant() || valueColumn.isFoldedDouble()) {
                perRowOnly.add(key);
                return null;
            }
            values = PreparedKeyedValues.build(computed.getRows(), correlation.keyCount());
            memo.recordKeyedValues(groupedQuery, values);
        }
        return values;
    }

    /**
     * A copy's equality correlation, analyzed once: null where it has none, or where a part of it answers anew each
     * time it is computed, or its outer key carries a collation.
     */
    private EqualityCorrelation equalityCorrelationOf(final String key, final SubqueryExpression body) {
        if (perRowOnly.contains(key)) {
            return null;
        }
        EqualityCorrelation correlation = equalityCorrelations.get(key);
        if (correlation != null) {
            return correlation;
        }
        correlation = analyzedCorrelation(key, body);
        if (correlation == null) {
            perRowOnly.add(key);
            return null;
        }
        equalityCorrelations.put(key, correlation);
        return correlation;
    }

    private EqualityCorrelation analyzedCorrelation(final String key, final SubqueryExpression body) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(body.getSubquery());
        if (parsed == null) {
            return null;
        }
        final Set<String> outerNames = new HashSet<>();
        for (final String name : buildLateralContext().keySet()) {
            outerNames.add(name.toUpperCase(Locale.ROOT));
        }
        final EqualityCorrelation correlation =
            new CorrelatedSubqueryRule(queryExecutor, outerNames).equalityCorrelation(parsed);
        if (correlation == null) {
            return null;
        }
        try {
            final List<String> innerTexts = correlation.innerTexts();
            if (correlation.getAggregateItem() != null) {
                innerTexts.add(correlation.getAggregateItem());
            }
            for (final String text : innerTexts) {
                if (VolatileCalls.writes(ExpressionEvaluator.parse(text))) {
                    return null;
                }
            }
            final List<Expression> outer = new ArrayList<>();
            for (final String text : correlation.getOuterKeys()) {
                final Expression outerKey = ExpressionEvaluator.parse(text);
                if (VolatileCalls.writes(outerKey) || visitor.collationOf(outerKey).toRules() != null) {
                    return null;
                }
                outer.add(outerKey);
            }
            outerKeys.put(key, outer);
        } catch (final RuntimeException unparsed) {
            return null;
        }
        return correlation;
    }

    /**
     * A correlation's once-for-all query, run as a subquery of its own; null — and the copy runs for each row from
     * then on — where it does not run, reads the outer row after all, or answers a key under a collation.
     */
    private ResultSet computedOnce(final String key, final String query, final int width) {
        final SubqueryMemo memo = visitor.getSubqueryMemo();
        final List<ResultSet> results;
        try {
            results = executeSubquery(query);
        } catch (final RuntimeException failed) {
            perRowOnly.add(key);
            return null;
        }
        if (memo.cachedResult(query) == null || results.isEmpty() || results.get(0) == null
                || results.get(0).getColumns().size() != width
                || columnCollation(results.get(0)).toRules() != null) {
            perRowOnly.add(key);
            return null;
        }
        return results.get(0);
    }

    /** The outer row's keys of a copy's equality correlation, or null where they cannot be read for the row. */
    private List<Object> outerKeyValues(final String key) {
        final List<Object> values = new ArrayList<>();
        try {
            for (final Expression outer : outerKeys.get(key)) {
                values.add(DeferredFault.read(outer.accept(visitor)));
            }
        } catch (final RuntimeException unread) {
            return null;
        }
        return values;
    }

    /** Whether one of a probe's keys is NULL, which no inner key equals. */
    private static boolean holdsNull(final List<Object> probe) {
        for (final Object value : probe) {
            if (value == null) {
                return true;
            }
        }
        return false;
    }

    /** A scalar subquery's value for the row, the subquery run for it. */
    private Object scalarSubqueryValue(final SubqueryExpression expr) {
        // What an uncorrelated subquery raises is raised ahead of the rows that read it (see SubqueryAhead).
        final long outerReads = visitor.lateralReadCount();
        final List<ResultSet> results;
        try {
            results = executeSubquery(statisticsBody(expr));
        } catch (final RuntimeException failed) {
            throw SubqueryAhead.raised(visitor.getQueryExecutor(), expr.getSubquery(), failed,
                visitor.lateralReadCount() == outerReads);
        }

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
            throw SubqueryAhead.raised(visitor.getQueryExecutor(), expr.getSubquery(),
                new RuntimeException("Single-row subquery returns more than one row."),
                visitor.lateralReadCount() == outerReads);
        }
        if (!resultSet.getColumns().isEmpty() && resultSet.getColumns().get(0).isUncheckedConstant()
                && readsOneProjection(expr)) {
            visitor.noteUncheckedConstantSubquery(expr);
        }
        if (!resultSet.getColumns().isEmpty() && resultSet.getColumns().get(0).isFoldedDouble()) {
            visitor.noteFoldedDoubleSubquery(expr);
        }

        return DeferredFault.read(resultSet.getRows().get(0).getValue(0));
    }

    /**
     * Whether a scalar subquery is one projection the plan folds with its source. A set operator or an ORDER
     * BY at its top keeps it apart, and so does a LIMIT, FETCH or TOP over a FROM: a cast of
     * {@code (SELECT v FROM c)} over a folded wrap converts unchecked, and one of
     * {@code (SELECT v FROM c LIMIT 1)}, {@code (SELECT TO_VARIANT(123) ORDER BY 1)} or a UNION of such arms
     * is checked, while {@code (SELECT TO_VARIANT(123) LIMIT 1)} is not (live-verified).
     */
    private boolean readsOneProjection(final SubqueryExpression expr) {
        final FrostlakeParser.SelectStatementContext parsed =
            visitor.getQueryExecutor().subqueryStatement(expr.getSubquery());
        if (parsed == null || !parsed.setOperator().isEmpty() || parsed.orderByClause() != null) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = parsed.selectOperand(0).selectClause();
        if (select == null) {
            return false;
        }
        final boolean limited = parsed.limitClause() != null || parsed.fetchClause() != null
            || select.topClause() != null;
        return !limited || select.tableExpression() == null;
    }

    Object evaluateInSubquery(final Object value, final SubqueryExpression expr, final boolean not,
                              final ExpressionCollation subjectCollation, final Expression subject) {
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        if (queryExecutor == null) {
            throw new RuntimeException("Cannot evaluate IN subquery: QueryExecutor not available");
        }

        // The COPY's key, so the membership index is looked up under the same name its result was
        // cached under.
        final String subquery = memoKey(expr);
        if (requireSupportedCorrelation(expr, false, true, not, subject)) {
            return null;
        }
        final List<ResultSet> results = executeSubquery(expr);

        if (results.isEmpty()) {
            return not;
        }

        final ResultSet resultSet = results.get(0);
        // The members compare under the collation the probe and the query's column settle on, as a comparison of
        // the two does: the column's own COLLATE counts (live-verified).
        final CollationSpec collation = ExpressionCollation.combine(columnCollation(resultSet), subjectCollation)
            .toRules();

        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        // A query that reads the row around it is matched row against row, as a join is: its IN is TRUE for a
        // match and FALSE for everything else, a NULL probe and a NULL in the set included, where its NOT IN keeps
        // the three values (live-verified).
        final boolean twoValued = !not && subqueryMemo != null && subqueryMemo.isCorrelated(subquery);
        // A NULL probe that reads no column is folded with its membership, which is UNKNOWN whatever the set
        // holds: NULL IN (…) over an empty set and over a query reading the row around it (live-verified).
        if (value == null && nullBeforeAnyRow(subject)) {
            return null;
        }
        // An empty set holds nothing: FALSE for IN and TRUE for NOT IN, a NULL probe read from a row included
        // (live-verified).
        if (resultSet.getRowCount() == 0) {
            return not;
        }
        // Three-valued logic otherwise (live-verified), mirroring the literal-list IN in
        // ExpressionEvaluatorVisitor.visitIn: a NULL probe is UNKNOWN, and a miss over a set that contains a
        // NULL member is UNKNOWN for both.
        if (value == null) {
            return twoValued ? Boolean.FALSE : null;
        }
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
            if (set.answers(value)) {
                if (set.contains(value)) {
                    return !not;
                }
                return set.hasNull() ? null : not;
            }
        }

        // Correlated, or a probe the index cannot settle: each member compared as value = member compares them.
        // A probe that is no text converts every member, as the account converts the query's column, so a member
        // that does not read refuses the statement wherever the match stands: 2 IN ('02', 'x') raises for 'x'
        // (live-verified).
        final boolean convertsEvery = !(value instanceof String);
        boolean anyNull = false;
        boolean matched = false;
        for (final Row subRow : resultSet.getRows()) {
            final Object subValue = DeferredFault.read(subRow.getValue(0));
            if (subValue == null) {
                anyNull = true;
                continue;
            }
            if (collation != null ? CollationMatching.equalUnder(collation, value, subValue)
                    : ExpressionArithmetic.equals(value, subValue)) {
                matched = true;
                if (!convertsEvery) {
                    break;
                }
            }
        }
        if (matched) {
            return !not;
        }
        return anyNull && !twoValued ? null : not;
    }

    /** The collation a query's one column carries, as a comparison with it would read it. */
    static ExpressionCollation columnCollation(final ResultSet resultSet) {
        if (resultSet.getColumns().isEmpty()) {
            return ExpressionCollation.NONE;
        }
        final ResultSetColumn column = resultSet.getColumns().get(0);
        if (column.getCollation() == null) {
            return ExpressionCollation.NONE;
        }
        return column.isCollationExplicit() ? ExpressionCollation.explicit(column.getCollation())
            : ExpressionCollation.column(column.getCollation());
    }

    /**
     * Whether an operand reads no column of the row it is evaluated over, so the plan folds it before any row
     * is read; false where the operand is not known.
     */
    boolean readsNoColumn(final Expression operand) {
        if (operand == null) {
            return false;
        }
        final ColumnReferenceCollectWalk walk = new ColumnReferenceCollectWalk();
        operand.accept(walk);
        return walk.references().isEmpty();
    }

    /**
     * Whether a probe is NULL before any row is read: it reads no column (see {@link #readsNoColumn}), or the plan
     * knows it holds NULL alone — a derived relation's NULL constant, its CAST(NULL AS INT) or MAX(NULL), through a
     * LIMIT or a join — which a membership folds with as it folds a NULL written there (live-verified).
     *
     * @param operand the probe
     * @return whether it is NULL whatever row is read
     */
    boolean nullBeforeAnyRow(final Expression operand) {
        if (readsNoColumn(operand)) {
            return true;
        }
        final ValueRange range = visitor.inferStaticRange(operand);
        return range != null && range.isEmpty() && range.isNullable();
    }

    /**
     * Whether a query read the row around it when it last ran: one matched row against row, whose positive
     * membership is two-valued (see {@link #evaluateInSubquery}).
     */
    boolean readsOuterRow(final SubqueryExpression expr) {
        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        return subqueryMemo != null && subqueryMemo.isCorrelated(memoKey(expr));
    }

    /**
     * Execute a subquery for the row at hand (see {@link #executeSubquery(String)}), counting every position
     * inside it from where its own text begins.
     */
    List<ResultSet> executeSubquery(final SubqueryExpression expr) {
        // A subquery whose values are read raises its own items' faults as it computes them (see RelationBody).
        final boolean outerBody = RelationBody.suspend();
        LateConstantRefusal.beginRowValue();
        try {
            return positionedSubquery(expr);
        } finally {
            LateConstantRefusal.endRowValue();
            RelationBody.end(outerBody);
        }
    }

    /** The rows an EXISTS counts: nothing reads their items, so a row fault waits in its cell. */
    private List<ResultSet> countedSubquery(final SubqueryExpression expr) {
        final boolean outerBody = RelationBody.begin();
        LateConstantRefusal.beginRowValue();
        try {
            return positionedSubquery(expr);
        } finally {
            LateConstantRefusal.endRowValue();
            RelationBody.end(outerBody);
        }
    }

    private List<ResultSet> positionedSubquery(final SubqueryExpression expr) {
        if (expr.getQueryPosition() == null || !ExpressionSource.hasOrigin()) {
            return executeSubquery(expr.getSubquery(), memoKey(expr));
        }
        final SourcePosition displaced = ExpressionSource.beginNested(expr.getQueryPosition());
        try {
            return executeSubquery(expr.getSubquery(), memoKey(expr));
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Compile a subquery before any row reaches it: plan it for its shape alone (see
     * {@link RelationShapeOnly}) with this scope's names bound to NULL as its outer names, inside
     * {@link SubqueryCompilation}, so every plan-time rule judges it as it judges a statement. A name it
     * cannot resolve is refused here. Any other refusal of its compilation is handed back for the caller
     * to raise once the statement around it has been judged, which is the order live reports them in: an
     * outer call's arity before a subquery's QUALIFY without a window. Under {@link SubqueryTypeRanking} a
     * refusal about types is raised here too, ahead of the enclosing query's own. What only a row can raise waits for
     * the row, and so does the refusal of a correlation live cannot evaluate, which live makes only where
     * its optimizer keeps the subquery (see {@link #requireSupportedCorrelation}).
     *
     * @param expr           the subquery
     * @param enclosingNames the names of the query around this scope's query when that one is compiling, or null
     * @return the subquery's refusal that waits for the statement around it, or null
     */
    RuntimeException compile(final SubqueryExpression expr, final Map<String, Object> enclosingNames) {
        final Map<String, Object> outerNames = compileScope(enclosingNames);
        final boolean ranksTypes = SubqueryTypeRanking.isActive();
        final SourcePosition displaced = ExpressionSource.beginNested(expr.getQueryPosition());
        final boolean shapeOnly = RelationShapeOnly.begin();
        final Map<String, Object> enclosing = SubqueryCompilation.begin(outerNames);
        final ExpressionEvaluatorVisitor enclosingScope = SubqueryCompilation.beginScope(visitor);
        final boolean enclosingRanking = SubqueryTypeRanking.begin(false);
        try {
            // The compiled shape says whether the subquery's select list hands out values a projection policy
            // restricts, which the select list holding it then hands out too (ProjectionLineage).
            final SubqueryExpression compiled = expr.isExistence() ? countedBody(expr, outerNames)
                : derivedBody(expr, outerNames);
            ProjectionLineage.recordSubquery(expr.getSubquery(), visitor.getQueryExecutor()
                .executeWithLateralContext(compiled.getSubquery(), outerNames));
            return null;
        } catch (final UnsupportedSubqueryException judgedPerRow) {
            return null;
        } catch (final RuntimeException refused) {
            if (!SqlCompilationError.isCompilationError(refused.getMessage())) {
                return null;
            }
            if (refusesAName(refused.getMessage()) || refusesACorrelatedWindow(refused.getMessage())
                    || refusesAnAggregatePlace(refused.getMessage())
                    || ranksTypes && SubqueryTypeRanking.ranksAheadOfEnclosingTypes(refused.getMessage())) {
                throw refused;
            }
            return refused;
        } finally {
            SubqueryTypeRanking.end(enclosingRanking);
            SubqueryCompilation.endScope(enclosingScope);
            SubqueryCompilation.end(enclosing);
            RelationShapeOnly.end(shapeOnly);
            ExpressionSource.end(displaced);
        }
    }

    /**
     * Whether a compilation refusal names a window call that reads the outer row, which live raises as it
     * compiles the statement, ahead of what the statement's rows would raise (see {@code WindowCorrelationRule}).
     */
    private static boolean refusesACorrelatedWindow(final String message) {
        return message.contains("Window function [") && message.contains("] contains a correlation.");
    }

    /**
     * Whether a compilation refusal is of a subquery standing where the aggregate it holds does not let it, which
     * live raises as it compiles the statement, once the names have resolved (see {@code CorrelatedAggregatePlace}).
     */
    private static boolean refusesAnAggregatePlace(final String message) {
        return message.contains("Subquery containing correlated aggregate function [");
    }

    /** Whether a compilation refusal is about a name: a column, a function or a relation nothing resolves. */
    static boolean refusesAName(final String message) {
        // A USING column either side lacks is its own capitalised sentence: "Invalid identifier N".
        return message.contains("invalid identifier") || message.contains("Invalid identifier ")
            || message.contains("Unknown function")
            || message.contains("does not exist or not authorized") || message.contains("ambiguous column name");
    }

    /**
     * The names a subquery compiled in this scope may read from it, each bound to NULL: every column of
     * every relation in scope, qualified by the name the query gives that relation (an alias hides the
     * table's own name) and bare, and the names of any query around this one.
     */
    private Map<String, Object> compileScope(final Map<String, Object> enclosingNames) {
        final Map<String, Object> names = new HashMap<>();
        if (enclosingNames != null) {
            names.putAll(enclosingNames);
        }
        final Table table = visitor.getTable();
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        final Set<String> columns = new HashSet<>();
        if (aliasToTable != null && !aliasToTable.isEmpty()) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                for (final TableColumn column : entry.getValue().getColumns()) {
                    names.put(entry.getKey() + "." + column.getName(), null);
                    names.put(column.getName(), null);
                    columns.add(column.getName());
                }
            }
        } else if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                names.put(table.getName() + "." + column.getName(), null);
            }
        }
        if (table != null) {
            for (final TableColumn column : table.getColumns()) {
                names.put(column.getName(), null);
                columns.add(column.getName());
            }
            for (final String shared : sharedBareNames(table, visitor.getMultiTableAllTables())) {
                names.put(shared, OuterNameBindings.AMBIGUOUS);
            }
        }
        OuterNameBindings.recordColumns(names, columns);
        return names;
    }

    /**
     * The bare names two relations of a joined row both carry, which live refuses as ambiguous: every one an ON
     * or a comma join repeats, a table joined to itself included, none of a USING or NATURAL join's, which read the
     * left relation's. Relations are told apart by instance, since a self-join's two instances share one name.
     */
    private static Set<String> sharedBareNames(final Table table, final List<Table> relations) {
        final Set<String> shared = new HashSet<>();
        if (relations == null || relations.size() < 2
                || table.getJoinKeyNames() != null && !table.getJoinKeyNames().isEmpty()) {
            return shared;
        }
        final Set<String> seen = new HashSet<>();
        final List<Table> counted = new ArrayList<>();
        for (final Table relation : relations) {
            if (countedAlready(counted, relation)) {
                continue;
            }
            counted.add(relation);
            final Set<String> own = new HashSet<>();
            for (final TableColumn column : relation.getColumns()) {
                own.add(column.getName());
            }
            for (final String name : own) {
                if (!seen.add(name)) {
                    shared.add(name);
                }
            }
        }
        return shared;
    }

    /** Whether a list holds this very relation instance. */
    private static boolean countedAlready(final List<Table> counted, final Table relation) {
        for (final Table seen : counted) {
            if (seen == relation) {
                return true;
            }
        }
        return false;
    }

    /**
     * Execute a subquery, memoizing the result of uncorrelated subqueries (those that read no outer
     * value during execution) so they run once per outer query instead of once per outer row. The
     * first execution probes the per-thread lateral-read counter; a zero delta proves the subquery is
     * uncorrelated and its result reusable. Correlated subqueries always re-execute. With no memo
     * configured this is exactly the previous behaviour (build context, execute).
     */
    List<ResultSet> executeSubquery(final String subquery) {
        return executeSubquery(subquery, subquery);
    }

    /**
     * Execute a subquery, memoizing it under {@code memoKey} — the COPY, where the text alone would make
     * two copies of one text share an answer the account computes twice.
     */
    private List<ResultSet> executeSubquery(final String subquery, final String memoKey) {
        final SubqueryMemo subqueryMemo = visitor.getSubqueryMemo();
        if (subqueryMemo != null) {
            final List<ResultSet> cached = subqueryMemo.cachedResult(memoKey);
            if (cached != null) {
                return cached;
            }
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final Map<String, Object> context = buildLateralContext();
        if (subqueryMemo == null || subqueryMemo.isCorrelated(memoKey)
                || subqueryMemo.isRedrawnPerRow(memoKey)) {
            return executeInScope(queryExecutor, subquery, context);
        }
        final long before = visitor.lateralReadCount();
        final long drawnBefore = visitor.rowDrawCount();
        final List<ResultSet> results = executeInScope(queryExecutor, subquery, context);
        if (visitor.rowDrawCount() != drawnBefore) {
            // It drew a value of its own: the next row gets another one, so no answer of its is reusable.
            subqueryMemo.recordRedrawnPerRow(memoKey);
        } else if (visitor.lateralReadCount() == before) {
            subqueryMemo.recordUncorrelated(memoKey, results);
        } else {
            subqueryMemo.recordCorrelated(memoKey);
        }
        return results;
    }

    /**
     * The memo key for one COPY of a subquery: its text and the place that copy was written at. Two
     * copies of one text are two subqueries, and a copy keeps its key across the outer rows.
     */
    private static String memoKey(final SubqueryExpression expr) {
        final SourcePosition at = expr.getQueryPosition() != null ? expr.getQueryPosition() : expr.getPosition();
        return at == null ? expr.getSubquery()
            : expr.getSubquery() + "@" + at.getLine() + ":" + at.getCharPositionInLine();
    }

    /** Run a subquery for a row with this scope named as the one around it (see {@link SubqueryCompilation#outerScope}). */
    private List<ResultSet> executeInScope(final QueryExecutor queryExecutor, final String subquery,
                                           final Map<String, Object> context) {
        final ExpressionEvaluatorVisitor enclosingScope = SubqueryCompilation.beginScope(visitor);
        try {
            return queryExecutor.executeWithLateralContext(subquery, context);
        } finally {
            SubqueryCompilation.endScope(enclosingScope);
        }
    }

    /**
     * Refuse a correlated subquery whose shape live cannot evaluate (see {@link CorrelatedSubqueryRule}) the
     * first time a row reaches it, so an outer query that reads no row never meets the refusal, as live's
     * never does. Each subquery is judged once per use for this evaluator. The refusal is positioned at
     * the subquery's SELECT, or at the EXISTS keyword of an EXISTS. A subquery the statement's plan already
     * refused (see {@link PlannedCorrelations}) is not run: its refusal waits for the statement.
     *
     * @param in      whether a membership is an IN rather than an EXISTS, which live limits differently
     * @param not     whether the IN is a NOT IN, which no semi-join answers
     * @param subject the IN's left operand, or null
     * @return true when the statement's plan refused the subquery, which the caller then reads as empty
     */
    private boolean requireSupportedCorrelation(final SubqueryExpression expr, final boolean scalar,
                                                final boolean in, final boolean not, final Expression subject) {
        return requireSupportedCorrelation(expr, expr, scalar, in, not, subject, expr.getPosition());
    }

    /**
     * {@link #requireSupportedCorrelation(SubqueryExpression, boolean, boolean, boolean, Expression)} for a
     * quantified comparison's query, judged as an IN's is: under ANY or SOME as a membership, under ALL as a NOT IN,
     * and refused at the quantifier, whatever the comparison operator (live-verified).
     *
     * @param expr the quantified comparison
     * @return true when the statement's plan refused the query, which the caller then reads as empty
     */
    boolean requireSupportedQuantified(final QuantifiedComparisonExpression expr) {
        final SubqueryExpression query = (SubqueryExpression) expr.getSubquery();
        final boolean all = expr.getQuantifier() == Quantifier.ALL;
        final boolean equality = !all && expr.getOperator() == BinaryOperator.EQUAL;
        return requireSupportedCorrelation(query, query, false, true, all, equality ? expr.getLeft() : null,
            expr.getQuantifierPosition());
    }

    /**
     * {@link #requireSupportedCorrelation(SubqueryExpression, boolean, boolean, boolean, Expression)} for a
     * subquery run as {@code run} where it is written as {@code expr}: the shape judged is the one run.
     *
     * @param place where a refusal is placed
     */
    private boolean requireSupportedCorrelation(final SubqueryExpression expr, final SubqueryExpression run,
                                                final boolean scalar, final boolean in, final boolean not,
                                                final Expression subject, final SourcePosition place) {
        final String key = scalar ? PlannedCorrelations.valueKey(expr.getSubquery())
            : PlannedCorrelations.membershipKey(expr.getSubquery());
        if (PlannedCorrelations.refused(key)) {
            return true;
        }
        if (judgedCorrelations.contains(key)) {
            return false;
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(run.getSubquery());
        if (parsed != null && !outerHoldsOneRowAtMost(queryExecutor)) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase(Locale.ROOT));
            }
            final Map<String, Boolean> pinned = new HashMap<>();
            final Map<String, Boolean> varying = new HashMap<>();
            final OuterNameConstancy constancy = new OuterNameConstancy() {
                @Override
                public boolean holdsOneValue(final String qualifier, final String column) {
                    final String key = qualifier == null ? column : qualifier + "." + column;
                    Boolean holds = pinned.get(key);
                    if (holds == null) {
                        holds = Boolean.valueOf(holdsOneValueInScope(qualifier, column));
                        pinned.put(key, holds);
                    }
                    return holds.booleanValue();
                }

                @Override
                public boolean variesAcrossRows(final String qualifier, final String column) {
                    if (holdsOneValue(qualifier, column)) {
                        return false;
                    }
                    final String key = qualifier == null ? column : qualifier + "." + column;
                    Boolean varies = varying.get(key);
                    if (varies == null) {
                        varies = Boolean.valueOf(variesInScope(qualifier, column));
                        varying.put(key, varies);
                    }
                    return varies.booleanValue();
                }
            };
            final CorrelatedSubqueryRule rule =
                new CorrelatedSubqueryRule(queryExecutor, outerNames, visitor.scopeRelations(), constancy);
            // Where the IN stands is not known here, so a positive one is read as the semi-join a WHERE makes of it;
            // the statement's plan has refused it already where it stands anywhere else.
            if (scalar ? rule.refusesScalar(parsed)
                    : rule.refusesMembership(parsed, in, in && !not, subject == null ? null : printed(subject),
                        in && not)) {
                final Function called = scalar ? visitor.sqlFunctionBeingCalled() : null;
                if (called != null) {
                    throw UnsupportedSubqueryException.inside(called);
                }
                final SourcePosition at = ExpressionSource.resolve(place);
                throw new UnsupportedSubqueryException(SqlCompilationError.of("Unsupported subquery type cannot be evaluated"
                    + (at == null ? "" : " at line " + at.getLine() + ", position " + at.getCharPositionInLine())));
            }
        }
        judgedCorrelations.add(key);
        return false;
    }

    /** An expression written back as text the rule can read, or null where it cannot be printed. */
    private static String printed(final Expression expression) {
        try {
            return AstPrinterVisitor.print(expression);
        } catch (final RuntimeException unprintable) {
            return null;
        }
    }

    /**
     * Whether this scope's statistics pin a name of its row to one value that is never NULL (see
     * {@link OuterNameConstancy}): the interval a number carries, or a catalog column of any scalar family whose
     * rows hold one value (see {@link StoredColumnValues}) on a relation no outer join extends with NULLs. A derived
     * relation's column is one where it passes such a column through or projects a literal that is not NULL (see
     * {@link DerivedColumnLineage}). A name a row further out binds carries no statistics here and is never one.
     */
    private boolean holdsOneValueInScope(final String qualifier, final String column) {
        if (visitor.getTable() == null || visitor.isScopeOpaqueToSubqueries()) {
            return false;
        }
        try {
            if (OuterNameConstancy.pinsOneValue(
                    visitor.inferStaticRange(new ColumnReferenceExpression(qualifier, column)))) {
                return true;
            }
            final Table owner = scopeRelationCarrying(qualifier, column);
            if (owner == null || mayBeNullExtended(owner)) {
                return false;
            }
            if (owner.residentSource() != null) {
                return new StoredColumnValues(visitor.getQueryExecutor()).holdsOneValue(owner, column);
            }
            final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, column);
            return lineage != null && (lineage.isLiteral() || lineage.storedTable() != null
                && new StoredColumnValues(visitor.getQueryExecutor())
                    .holdsOneValue(lineage.storedTable(), lineage.storedColumn()));
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /**
     * Whether this scope's statistics show a name of its row taking more than one value (see
     * {@link OuterNameConstancy#variesAcrossRows}), asked of a name they do not pin. A catalog table's column does,
     * and so does a name not placed in this scope. A derived relation's column does only where it passes a stored
     * column holding more than one value through (see {@link DerivedColumnLineage}), whatever a WHERE on the way
     * keeps: an expression or a column of a relation the plan keeps whole is a value the statistics do not reach.
     */
    private boolean variesInScope(final String qualifier, final String column) {
        if (visitor.getTable() == null || visitor.isScopeOpaqueToSubqueries()) {
            return true;
        }
        try {
            final Table owner = scopeRelationCarrying(qualifier, column);
            if (owner == null || owner.residentSource() != null) {
                return true;
            }
            final DerivedColumnLineage lineage = DerivedColumnLineage.of(owner, column);
            return lineage != null && !lineage.isLiteral() && !new StoredColumnValues(visitor.getQueryExecutor())
                .holdsOneValue(lineage.storedTable(), lineage.storedColumn());
        } catch (final RuntimeException unresolved) {
            return false;
        }
    }

    /**
     * The relation of this scope a name reads: the one its qualifier names, else the one relation carrying the
     * column, a join's merged relation aside; null where none or several do.
     */
    private Table scopeRelationCarrying(final String qualifier, final String column) {
        Table owner = null;
        for (final Map.Entry<String, Table> relation : visitor.scopeRelations().entrySet()) {
            final Table candidate = relation.getValue();
            if (qualifier != null) {
                if (relation.getKey().equalsIgnoreCase(qualifier)) {
                    return candidate;
                }
                continue;
            }
            if (candidate.getJoinedRelations() != null || !candidate.hasColumn(column) || candidate == owner) {
                continue;
            }
            if (owner != null) {
                return null;
            }
            owner = candidate;
        }
        return owner;
    }

    /** Whether the scope joins relations and an outer join may extend {@code owner} with NULLs its rows do not hold. */
    private boolean mayBeNullExtended(final Table owner) {
        final List<Table> relations = visitor.getMultiTableAllTables();
        if (relations == null || relations.size() < 2) {
            return false;
        }
        final JoinedRelations joined = visitor.getTable() == null ? null : visitor.getTable().getJoinedRelations();
        return joined == null || !joined.includes(owner) || joined.isNullExtended(owner);
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
                context.put(table.getName().toUpperCase(Locale.ROOT) + "." + colName.toUpperCase(Locale.ROOT), null);
                context.put(colName, null);
                context.put(colName.toUpperCase(Locale.ROOT), null);
            }
        }
        final Map<String, Table> aliasToTable = visitor.getMultiTableAliasToTable();
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                for (final TableColumn column : entry.getValue().getColumns()) {
                    context.put((entry.getKey() + "." + column.getName()).toUpperCase(Locale.ROOT), null);
                    context.put(entry.getKey() + "." + column.getName(), null);
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
        final Map<String, Object> outerRow = visitor.getSubqueryOuterRow();
        if (outerRow != null) {
            context.putAll(outerRow);
        }
        addRowBindings(context);
        return context;
    }

    /** The names a subquery reads the visitor's row by: bare, relation-qualified and alias-qualified. */
    Map<String, Object> rowBindings() {
        final Map<String, Object> bindings = new HashMap<>();
        addRowBindings(bindings);
        return bindings;
    }

    private void addRowBindings(final Map<String, Object> context) {
        final Table table = visitor.getTable();
        final Row row = visitor.getRow();
        if (table != null && row != null && !visitor.isScopeOpaqueToSubqueries()) {
            final List<TableColumn> columns = table.getColumns();
            // Every column under its EXACT spelling: live resolves "x" and X to two columns, and neither reaches
            // a column spelled the other way. This row's bindings replace any a wider scope already bound under the
            // same key. BARE names too — a correlated subquery may reference an outer column unqualified, notably
            // FLATTEN outputs (WHERE EXISTS (... WHERE k = VALUE:field)), which have no natural alias; the
            // subquery's own columns still win, since this context is consulted only after its own relations. A
            // bare name reads the FIRST column that carries it, the left relation's, and a name two relations of a
            // join carry is ambiguous.
            final Set<String> shared = sharedBareNames(table, visitor.getMultiTableAllTables());
            final Set<String> bare = new HashSet<>();
            for (int i = 0; i < columns.size(); i++) {
                final String colName = columns.get(i).getName();
                final Object value = row.getValue(i);
                context.put(table.getName() + "." + colName, value);
                if (bare.add(colName)) {
                    context.put(colName, shared.contains(colName) ? OuterNameBindings.AMBIGUOUS : value);
                }
            }
            OuterNameBindings.recordColumns(context, bare);
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
                    context.put(entry.getKey() + "." + aliased.getColumns().get(i).getName(),
                        row.getValue(offset + i));
                }
            }
        }
    }
}
