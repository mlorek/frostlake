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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * The ON conditions of one query block's joins, compiled as each join is reached, in the scope in force there: the
 * relations joined so far and the one being joined, with the select list's aliases readable. Live compiles a join's
 * condition with its statement, so an invalid identifier, an unknown function or a mistyped argument there is refused
 * over empty inputs too, where Frostlake judged the condition pair by pair and refused, without a position, only on a
 * pair it reached.
 *
 * <p>A condition that does not compile joins no rows, and its refusal waits for its rank among the statement's own
 * (live-verified):
 *
 * <ul>
 *   <li>an invalid identifier comes after the select list's names and ahead of the WHERE's and every later clause's;</li>
 *   <li>an unknown function joins the statement's one sentence naming them all, in written order;</li>
 *   <li>an argument type comes after every clause's names, the ORDER BY ordinal and the names inside the clauses'
 *       subqueries, and ahead of every other clause's types, the aggregate and window placement rules and the grouped
 *       select list; a condition that is no predicate comes after every condition's argument types.</li>
 * </ul>
 */
final class JoinConditionCompilation {

    private final QueryExecutor executor;
    private final FrostlakeParser.SelectClauseContext selectClause;
    /** The first invalid identifier, in join order. */
    private RuntimeException names;
    /** The first unresolvable function name. */
    private RuntimeException functionNames;
    /** The first argument type refusal. */
    private RuntimeException types;
    /** The first condition that is no predicate. */
    private RuntimeException predicates;
    /**
     * The first join shape refusal — an ASOF join's MATCH_CONDITION shape, or the restriction a lateral table function
     * meets with a join key — raised after every other waiting one.
     */
    private RuntimeException shapes;

    /**
     * The conditions of one query block.
     *
     * @param executor     the executor running the block
     * @param selectClause the block, whose select aliases a condition may read
     */
    JoinConditionCompilation(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext selectClause) {
        this.executor = executor;
        this.selectClause = selectClause;
    }

    /**
     * Compile one join's condition, by kind: its names, then its function names, then its argument types, then whether
     * it is a predicate at all. The first refusal of each kind is kept for its rank.
     *
     * @param condition    the condition as written
     * @param leftTable    the relation joined so far
     * @param aliasToTable every relation in scope, by the name the query gives it
     * @param allTables    every relation in scope, the one being joined last
     * @return true when the condition compiles, so its pairs may be judged
     */
    boolean compiles(final ParserRuleContext condition, final Table leftTable, final Map<String, Table> aliasToTable,
                     final List<Table> allTables) {
        return compiles(condition, leftTable, aliasToTable, allTables, true);
    }

    /**
     * Compile one join's condition as {@link #compiles(ParserRuleContext, Table, Map, List)} does, the predicate
     * judgement left out when {@code predicate} is false: an ASOF join's MATCH_CONDITION is judged by its operator
     * instead of its type (see {@link AsofJoinCompilation}).
     *
     * @param condition    the condition as written
     * @param leftTable    the relation joined so far
     * @param aliasToTable every relation in scope, by the name the query gives it
     * @param allTables    every relation in scope, the one being joined last
     * @param predicate    whether the condition must be a predicate
     * @return true when the condition compiles, so its pairs may be judged
     */
    boolean compiles(final ParserRuleContext condition, final Table leftTable, final Map<String, Table> aliasToTable,
                     final List<Table> allTables, final boolean predicate) {
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(executor.getOriginalText(condition));
        } catch (final RuntimeException unparseable) {
            return true;
        }
        final ExpressionEvaluator scope = new ExpressionEvaluator(leftTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        scope.setMultiTableContext(aliasToTable, allTables);
        scope.setScopeExemptNames(selectClause == null ? new HashSet<String>()
            : executor.selectItemAliasNames(selectClause));
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(
            condition.getStart().getLine(), condition.getStart().getCharPositionInLine()));
        try {
            final RuntimeException misnamed = refusal(scope, parsed, 0);
            if (misnamed != null) {
                names = names == null ? misnamed : names;
                return false;
            }
            final RuntimeException unknownFunction = refusal(scope, parsed, 1);
            if (unknownFunction != null) {
                functionNames = functionNames == null ? unknownFunction : functionNames;
                return false;
            }
            final RuntimeException mistyped = refusal(scope, parsed, 2);
            if (mistyped != null) {
                types = types == null ? mistyped : types;
                return false;
            }
            final RuntimeException noPredicate = predicate ? refusal(scope, aliasedItem(parsed), 3) : null;
            if (noPredicate != null) {
                predicates = predicates == null ? noPredicate : predicates;
                return false;
            }
            return true;
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * The select item a bare name in the condition names, or the condition as written. A join condition that
     * is a SELECT alias is judged — and echoed — as the expression the alias projects, where the same alias
     * in WHERE or HAVING is echoed by its own name: {@code SELECT f.a AS z FROM f JOIN t ON z} reads
     * "Invalid data type [NUMBER(38,0)] for predicate [F.A]" (live-verified).
     *
     * @param condition the condition as parsed
     * @return the item's expression, or the condition unchanged
     */
    private Expression aliasedItem(final Expression condition) {
        if (selectClause == null || !(condition instanceof ColumnReferenceExpression)) {
            return condition;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) condition;
        if (reference.isQualified()) {
            return condition;
        }
        for (final FrostlakeParser.SelectItemContext item : selectClause.selectList().selectItem()) {
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias == null || !SelectItemAccessors.isExprItem(item)
                    || !alias.equalsIgnoreCase(reference.getColumnName())) {
                continue;
            }
            try {
                return ExpressionEvaluator.parse(
                    executor.getOriginalText(SelectItemAccessors.getItemExpression(item)));
            } catch (final RuntimeException unparseable) {
                return condition;
            }
        }
        return condition;
    }

    /** One phase's compilation refusal, or null; a failure that is no compilation error is left to the pairs. */
    private static RuntimeException refusal(final ExpressionEvaluator scope, final Expression parsed, final int phase) {
        try {
            if (phase == 0) {
                scope.validateColumnScope(parsed);
            } else if (phase == 1) {
                scope.validateFunctionNames(parsed);
            } else if (phase == 2) {
                scope.validateStrict(parsed);
            } else {
                scope.validatePredicate(parsed);
            }
            return null;
        } catch (final RuntimeException refused) {
            return SqlCompilationError.isCompilationError(refused.getMessage()) ? refused : null;
        }
    }

    /** The first condition's invalid identifier, or null. */
    RuntimeException names() {
        return names;
    }

    /**
     * The first condition's argument type refusal, or else the first condition that is no predicate, when no condition
     * misnames anything; else null.
     */
    RuntimeException types() {
        if (names != null || functionNames != null) {
            return null;
        }
        return types != null ? types : predicates;
    }

    /**
     * Hold a join's shape refusal, judged once its condition compiled: it waits for the statement's names and types
     * and is raised after every other refusal held here (see {@link AsofJoinCompilation}).
     *
     * @param refused the refusal
     */
    void holdShape(final RuntimeException refused) {
        shapes = shapes == null ? refused : shapes;
    }

    /** Raise whichever refusal is still waiting, names first; a block whose conditions all compile passes. */
    void rejectWaiting() {
        if (names != null) {
            throw names;
        }
        if (functionNames != null) {
            throw functionNames;
        }
        if (types != null) {
            throw types;
        }
        if (predicates != null) {
            throw predicates;
        }
        if (shapes != null) {
            throw shapes;
        }
    }
}
