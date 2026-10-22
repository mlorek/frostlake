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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * SYSTEM$GET_TAG is answered while the statement compiles, ahead of every other judgement of the query's clauses: its
 * count, its constant and text arguments, its domain and its tag are refused once the FROM clause has resolved, before
 * an invalid identifier written ahead of it and before the kind of any call written with OVER, the call's own OVER
 * included (live-verified):
 *
 * <pre>
 *   SELECT nosuch, SYSTEM$GET_TAG(n, 'tt', 'TABLE') FROM t
 *       Invalid value [N] for function 'SYSTEM$GET_TAG', parameter 0: constant arguments expected
 *   SELECT ABS(i) OVER (), SYSTEM$GET_TAG('tt', 't', 'TABLE') FROM t
 *       Tag 'TT' does not exist or not authorized.
 *   SELECT ABS(i) OVER (), SYSTEM$GET_TAG('tt', 't', 'NOPE') FROM t
 *       Unknown domain: NOPE.
 *   SELECT SYSTEM$GET_TAG(n, 'tt') OVER () FROM t
 *       not enough arguments for function [SYSTEM$GET_TAG(N, 'tt')], expected 3, got 2
 *   SELECT ABS(i) OVER () FROM t WHERE SYSTEM$GET_TAG(n, 'tt', 'TABLE') = 'x'
 *       the constant-argument refusal
 * </pre>
 *
 * A table the FROM clause names that is not there is refused first, and a query nested in a clause judges its own
 * calls when it compiles.
 */
final class TagLookupCalls {

    private static final String NAME = "SYSTEM$GET_TAG";

    private TagLookupCalls() {
    }

    /**
     * Refuse the first SYSTEM$GET_TAG call of the select list, the WHERE, the HAVING or the QUALIFY that its own
     * compilation refuses, in that order.
     *
     * @param executor     the executor compiling the query
     * @param ctx          the query
     * @param table        the relation the FROM clause resolved
     * @param aliasToTable the FROM-clause keys
     * @param allTables    every relation in scope
     */
    static void judgeFirst(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext ctx,
                           final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        if (!ParseTreeText.getOriginalText(ctx).toUpperCase(Locale.ROOT).contains(NAME)) {
            return;
        }
        final List<ParserRuleContext> clauses = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final ParserRuleContext expression = SelectItemAccessors.getItemExpression(item);
            if (expression != null) {
                clauses.add(expression);
            }
        }
        if (ctx.whereClause() != null) {
            clauses.add(ctx.whereClause().booleanExpr());
        }
        if (ctx.havingClause() != null) {
            clauses.add(ctx.havingClause().booleanExpr());
        }
        if (ctx.qualifyClause() != null) {
            clauses.add(ctx.qualifyClause().booleanExpr());
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        if (aliasToTable != null && allTables != null && !allTables.isEmpty()) {
            evaluator.setMultiTableContext(aliasToTable, allTables);
        }
        for (final ParserRuleContext clause : clauses) {
            final List<ParserRuleContext> calls = new ArrayList<>();
            collect(clause, calls);
            for (final ParserRuleContext call : calls) {
                judge(call, evaluator);
            }
        }
    }

    /** Judge one call as its own compilation does, a windowed one as the call without its OVER. */
    private static void judge(final ParserRuleContext call, final ExpressionEvaluator evaluator) {
        String text = ParseTreeText.getOriginalText(call);
        final FrostlakeParser.OverClauseContext over = call instanceof FrostlakeParser.FunctionCallExprContext
            ? ((FrostlakeParser.FunctionCallExprContext) call).overClause() : null;
        if (over != null) {
            text = text.substring(0, over.getStart().getStartIndex() - call.getStart().getStartIndex()).trim();
        }
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(text);
        } catch (final RuntimeException unread) {
            return;
        }
        if (!(parsed instanceof FunctionCallExpression)) {
            return;
        }
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(call.getStart().getLine(), call.getStart().getCharPositionInLine()));
        try {
            evaluator.judgeTagLookup((FunctionCallExpression) parsed);
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * The SYSTEM$GET_TAG calls written under a node, outside the queries nested in it: in the call form of its own,
     * with OVER after it or not, and named as any other function is.
     */
    private static void collect(final ParseTree node, final List<ParserRuleContext> out) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.SystemFuncExprContext
                && NAME.equalsIgnoreCase(((FrostlakeParser.SystemFuncExprContext) node).SYSTEM_FUNC().getText())
                || node instanceof FrostlakeParser.FunctionCallExprContext
                && NAME.equalsIgnoreCase(((FrostlakeParser.FunctionCallExprContext) node).functionName().getText())) {
            out.add((ParserRuleContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), out);
        }
    }
}
