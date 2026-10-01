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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * A MERGE compiled once, before any row is read, so an empty target or source refuses what a full one
 * would. Live's order, with two faults written either way:
 *
 * <ol>
 *   <li>a WHEN clause no row can reach: {@code Unreachable merge case.} at its MATCHED, or at the NOT of a
 *       NOT MATCHED, when an earlier clause of its kind has no AND condition;</li>
 *   <li>a column SET twice or INSERTed twice, and an INSERT whose value list is not its column list's
 *       length;</li>
 *   <li>every subquery, each whole;</li>
 *   <li>the SET and INSERT target columns;</li>
 *   <li>the column references of the INSERT values, then of the SET values, then of the WHEN MATCHED
 *       conditions, the WHEN NOT MATCHED conditions and the ON condition; then every unknown function
 *       name, in one sentence, in the same order; then every argument type;</li>
 *   <li>an aggregate: in a SET value or a WHEN MATCHED condition as a grouping of the row identity, in the
 *       ON condition as that clause's own refusal.</li>
 * </ol>
 *
 * The ON condition, a WHEN MATCHED condition and a SET value read the target and the source; a WHEN NOT
 * MATCHED condition and an INSERT value read the source alone. A relation with an alias answers only to
 * that alias.
 */
final class MergeCompilation {

    private final QueryExecutor executor;

    MergeCompilation(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Compile the statement.
     *
     * @param ctx         the MERGE
     * @param target      the target table
     * @param targetAlias the target's alias, or null
     * @param source      the source relation's shape
     * @param sourceAlias the source's alias, or null
     * @param namesKnown  whether the source's column names are known; a VALUES source is judged for its
     *                    structure alone
     * @return a subquery's refusal that waits for the rest of the statement to be judged, or null
     */
    RuntimeException compile(final FrostlakeParser.MergeStatementContext ctx, final Table target,
                              final String targetAlias, final Table source, final String sourceAlias,
                              final boolean namesKnown) {
        rejectUnreachableClauses(ctx);
        rejectMalformedBranches(ctx, target);
        if (!namesKnown || source == null) {
            return null;
        }
        final Map<String, Table> bothAliases = new HashMap<>();
        final List<Table> bothTables = new ArrayList<>();
        bothAliases.put(relationName(target, targetAlias), target);
        bothTables.add(target);
        bothAliases.put(relationName(source, sourceAlias), source);
        bothTables.add(source);
        final Table merged = executor.mergeTableMetadata(target, source);
        final ExpressionEvaluator both = new ExpressionEvaluator(merged, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        both.setMultiTableContext(bothAliases, bothTables);
        final Map<String, Table> sourceAliases = new HashMap<>();
        final List<Table> sourceTables = new ArrayList<>();
        sourceAliases.put(relationName(source, sourceAlias), source);
        sourceTables.add(source);
        final ExpressionEvaluator sourceOnly = new ExpressionEvaluator(source, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        sourceOnly.setMultiTableContext(sourceAliases, sourceTables);

        // Every expression, in the order written, for the subqueries.
        final List<ParserRuleContext> written = new ArrayList<>();
        written.add(ctx.booleanExpr());
        // The same expressions in live's order for everything else, each with the scope it reads.
        final List<ParserRuleContext> insertValues = new ArrayList<>();
        final List<ParserRuleContext> setValues = new ArrayList<>();
        final List<ParserRuleContext> matchedConditions = new ArrayList<>();
        final List<ParserRuleContext> notMatchedConditions = new ArrayList<>();
        for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
            final boolean notMatched = clause.NOT() != null;
            if (clause.booleanExpr() != null) {
                written.add(clause.booleanExpr());
                (notMatched ? notMatchedConditions : matchedConditions).add(clause.booleanExpr());
            }
            if (clause.assignmentList() != null) {
                for (final FrostlakeParser.AssignmentContext assign : clause.assignmentList().assignment()) {
                    written.add(assign.expression());
                    setValues.add(assign.expression());
                }
            }
            if (clause.valueTuple() != null && clause.valueTuple().valueList() != null) {
                for (final FrostlakeParser.BooleanExprContext value : clause.valueTuple().valueList().booleanExpr()) {
                    written.add(value);
                    insertValues.add(value);
                }
            }
        }
        final RuntimeException subqueryRefusal =
            executor.compileSubqueriesIn(written, merged, bothAliases, bothTables, null);
        rejectUnknownTargets(ctx, target, targetAlias);

        final List<ParserRuleContext> ordered = new ArrayList<>();
        ordered.addAll(insertValues);
        ordered.addAll(setValues);
        ordered.addAll(matchedConditions);
        ordered.addAll(notMatchedConditions);
        ordered.add(ctx.booleanExpr());
        final Set<ParserRuleContext> readSourceOnly = new HashSet<>();
        readSourceOnly.addAll(insertValues);
        readSourceOnly.addAll(notMatchedConditions);
        for (final ParserRuleContext expression : ordered) {
            judge(readSourceOnly.contains(expression) ? sourceOnly : both, expression, false);
        }
        final List<FunctionCallExpression> unknown = executor.unresolvableCallsIn(ordered);
        if (!unknown.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(both.unknownFunctionSentence(unknown)));
        }
        // Only a BOOLEAN is a predicate, and the ON's own type is judged ahead of every argument type (live-verified).
        judgePredicate(both, ctx.booleanExpr());
        for (final ParserRuleContext expression : ordered) {
            judge(readSourceOnly.contains(expression) ? sourceOnly : both, expression, true);
        }
        final List<ParserRuleContext> grouped = new ArrayList<>();
        grouped.addAll(setValues);
        grouped.addAll(matchedConditions);
        for (final ParserRuleContext expression : grouped) {
            if (executor.holdsAggregate(expression)) {
                throw new RuntimeException(SqlCompilationError.of(
                    "[PSEUDOCOLUMN(VOL_ID)] is not a valid group by expression"));
            }
        }
        executor.rejectAggregatesInClause(ctx.booleanExpr(), "ON clause", merged, bothAliases, bothTables);
        return subqueryRefusal;
    }

    /** One expression's column references, or its argument types, in its scope and at its place. */
    private void judge(final ExpressionEvaluator scope, final ParserRuleContext expression, final boolean types) {
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
            expression.getStart().getLine(), expression.getStart().getCharPositionInLine()));
        try {
            final Expression parsed = ExpressionEvaluator.parse(executor.getOriginalText(expression));
            if (types) {
                scope.validateStrict(parsed);
            } else {
                scope.validateColumnScope(parsed);
            }
        } catch (final RuntimeException unjudged) {
            if (SqlCompilationError.isCompilationError(unjudged.getMessage())) {
                throw unjudged;
            }
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** The ON condition's own type, at its place: only a BOOLEAN is a predicate. */
    private void judgePredicate(final ExpressionEvaluator scope, final ParserRuleContext condition) {
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
            condition.getStart().getLine(), condition.getStart().getCharPositionInLine()));
        try {
            scope.validatePredicate(ExpressionEvaluator.parse(executor.getOriginalText(condition)));
        } catch (final RuntimeException unjudged) {
            if (SqlCompilationError.isCompilationError(unjudged.getMessage())) {
                throw unjudged;
            }
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** A later clause of a kind whose earlier clause has no condition can never be reached. */
    private void rejectUnreachableClauses(final FrostlakeParser.MergeStatementContext ctx) {
        boolean matchedCatchesAll = false;
        boolean notMatchedCatchesAll = false;
        for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
            final boolean notMatched = clause.NOT() != null;
            if (notMatched ? notMatchedCatchesAll : matchedCatchesAll) {
                final Token at = notMatched ? clause.NOT().getSymbol() : clause.MATCHED().getSymbol();
                throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                    "Unreachable merge case."));
            }
            if (clause.booleanExpr() == null) {
                if (notMatched) {
                    notMatchedCatchesAll = true;
                } else {
                    matchedCatchesAll = true;
                }
            }
        }
    }

    /** A column named twice in one SET or one INSERT, and an INSERT whose values do not fit its columns. */
    private void rejectMalformedBranches(final FrostlakeParser.MergeStatementContext ctx, final Table target) {
        for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
            final Set<String> named = new HashSet<>();
            if (clause.assignmentList() != null) {
                for (final FrostlakeParser.AssignmentContext assign : clause.assignmentList().assignment()) {
                    final String column = ParseTreeText.namePartText(assign.namePart());
                    // Keyed by the canonical spelling: "x" and "X" are two columns, not one named twice.
                    if (!named.add(column)) {
                        throw duplicateColumn(column);
                    }
                }
            }
            if (clause.INSERT() == null) {
                continue;
            }
            int columns = target.getColumns().size();
            if (clause.mergeInsertColumnList() != null) {
                columns = clause.mergeInsertColumnList().mergeInsertColumn().size();
                for (final FrostlakeParser.MergeInsertColumnContext column
                        : clause.mergeInsertColumnList().mergeInsertColumn()) {
                    final String name = insertColumnName(column);
                    if (!named.add(name)) {
                        throw duplicateColumn(name);
                    }
                }
            }
            final int values = clause.valueTuple() == null || clause.valueTuple().valueList() == null
                ? 0 : clause.valueTuple().valueList().booleanExpr().size();
            if (values != columns) {
                throw new RuntimeException(SqlCompilationError.of(
                    "Insert value list does not match column list expecting " + columns + " but got " + values));
            }
        }
    }

    /** Each SET and INSERT target is a column of the target, and a SET target's qualifier names the target. */
    private void rejectUnknownTargets(final FrostlakeParser.MergeStatementContext ctx, final Table target,
                                      final String targetAlias) {
        for (final FrostlakeParser.MergeClauseContext clause : ctx.mergeClause()) {
            if (clause.assignmentList() != null) {
                for (final FrostlakeParser.AssignmentContext assign : clause.assignmentList().assignment()) {
                    final String column = ParseTreeText.namePartText(assign.namePart());
                    if (assign.identifier() != null) {
                        final String qualifier = executor.getIdentifier(assign.identifier());
                        if (!qualifier.equalsIgnoreCase(target.getName())
                                && !(targetAlias != null && qualifier.equalsIgnoreCase(targetAlias))) {
                            throw new RuntimeException(SqlCompilationError.invalidIdentifier(
                                assign.getStart().getLine(), assign.getStart().getCharPositionInLine(),
                                qualifier.toUpperCase() + "." + column.toUpperCase()));
                        }
                    }
                    requireColumn(target, column, assign.namePart());
                }
            }
            if (clause.mergeInsertColumnList() != null) {
                for (final FrostlakeParser.MergeInsertColumnContext column
                        : clause.mergeInsertColumnList().mergeInsertColumn()) {
                    final List<FrostlakeParser.IdentifierContext> parts = column.identifier();
                    requireColumn(target, insertColumnName(column), parts.get(parts.size() - 1));
                }
            }
        }
    }

    private void requireColumn(final Table target, final String column, final ParserRuleContext at) {
        try {
            executor.getColumnIndex(target, column);
        } catch (final RuntimeException unknown) {
            throw new RuntimeException(SqlCompilationError.invalidIdentifier(at.getStart().getLine(),
                at.getStart().getCharPositionInLine(), column.toUpperCase()), unknown);
        }
    }

    private String insertColumnName(final FrostlakeParser.MergeInsertColumnContext column) {
        final List<FrostlakeParser.IdentifierContext> parts = column.identifier();
        return executor.getIdentifier(parts.get(parts.size() - 1));
    }

    private static RuntimeException duplicateColumn(final String column) {
        return new RuntimeException(SqlCompilationError.of(
            "duplicate column name '" + SqlIdentifiers.spellCanonical(column) + "'"));
    }

    private static String relationName(final Table relation, final String alias) {
        return (alias != null ? alias : relation.getName()).toUpperCase();
    }
}
