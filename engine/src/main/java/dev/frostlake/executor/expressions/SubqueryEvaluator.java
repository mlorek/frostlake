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
import dev.frostlake.executor.OuterNameConstancy;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RelationBody;
import dev.frostlake.executor.RelationShapeOnly;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StoredColumnValues;
import dev.frostlake.executor.SubqueryAhead;
import dev.frostlake.executor.SubqueryCompilation;
import dev.frostlake.functions.CollationMatching;
import dev.frostlake.metastore.model.JoinedRelations;
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

        if (requireSupportedCorrelation(expr, false, false, false, null)) {
            return false;
        }
        final List<ResultSet> results = countedSubquery(expr);

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

        if (requireSupportedCorrelation(expr, true, false, false, null)) {
            return null;
        }
        // What an uncorrelated subquery raises is raised ahead of the rows that read it (see SubqueryAhead).
        final long outerReads = visitor.lateralReadCount();
        final List<ResultSet> results;
        try {
            results = executeSubquery(expr);
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
                              final CollationSpec collation, final Expression subject) {
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
            final Object subValue = DeferredFault.read(subRow.getValue(0));
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
            visitor.getQueryExecutor().executeWithLateralContext(expr.getSubquery(), outerNames);
            return null;
        } catch (final UnsupportedSubqueryException judgedPerRow) {
            return null;
        } catch (final RuntimeException refused) {
            if (!SqlCompilationError.isCompilationError(refused.getMessage())) {
                return null;
            }
            if (refusesAName(refused.getMessage()) || refusesACorrelatedWindow(refused.getMessage())
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
        final String key = scalar ? PlannedCorrelations.valueKey(expr.getSubquery())
            : PlannedCorrelations.membershipKey(expr.getSubquery());
        if (PlannedCorrelations.refused(key)) {
            return true;
        }
        if (judgedCorrelations.contains(key)) {
            return false;
        }
        final QueryExecutor queryExecutor = visitor.getQueryExecutor();
        final FrostlakeParser.SelectStatementContext parsed = queryExecutor.subqueryStatement(expr.getSubquery());
        if (parsed != null && !outerHoldsOneRowAtMost(queryExecutor)) {
            final Set<String> outerNames = new HashSet<>();
            for (final String name : buildLateralContext().keySet()) {
                outerNames.add(name.toUpperCase());
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
                    : rule.refusesMembership(parsed, in, in && !not, subject == null ? null : printed(subject))) {
                final SourcePosition at = ExpressionSource.resolve(expr.getPosition());
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
