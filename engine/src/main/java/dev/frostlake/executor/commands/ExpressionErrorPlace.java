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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ScriptedErrorPlace;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the compilation error inside an uncaught EXPRESSION_ERROR stands, as the account reports it. A
 * scalar subquery in a scripting expression compiles as a query of its own and reports against its own
 * text; the account reports against the whole expression, counted as though the expression were written
 * after a fixed lead-in — fifteen columns for a value assigned to a variable (a LET, an assignment, a
 * DECLARE default) and seven for any other expression (a RETURN, an IF or WHILE condition, a CASE operand)
 * — and only on the expression's first line: {@code LET a NUMBER := (SELECT missing FROM …)} reports
 * position 23 and {@code RETURN 1 + (SELECT missing …)} position 19, while a name on the expression's
 * second line keeps its own column (all live-verified).
 */
final class ExpressionErrorPlace {

    /** The lead-in a value assigned to a variable is counted after. */
    private static final int ASSIGNED_VALUE_LEAD = 15;

    /** The lead-in any other scripting expression is counted after. */
    private static final int EXPRESSION_LEAD = 7;

    private ExpressionErrorPlace() {
    }

    /**
     * The message placed in the failing expression, or unchanged when its place cannot be told.
     *
     * @param block   the block the expression stands in
     * @param line    the expression's line in the block
     * @param column  the expression's column
     * @param message the error the expression's subquery raised
     * @return the message as the account words it
     */
    static String placed(final ParseTree block, final int line, final int column, final String message) {
        if (ScriptedErrorPlace.placeOf(message) == null) {
            return message;
        }
        final ParserRuleContext expression = expressionAt(block, line, column);
        final FrostlakeParser.SelectStatementContext query = expression == null ? null
            : failingQuery(expression, message);
        if (query == null) {
            return message;
        }
        final String inBlock = ScriptedErrorPlace.inEnclosingText(message,
            query.getStart().getLine(), query.getStart().getCharPositionInLine());
        return ScriptedErrorPlace.inFragment(inBlock, line, column,
            assignsAValue(expression) ? ASSIGNED_VALUE_LEAD : EXPRESSION_LEAD);
    }

    /** The outermost expression that starts at the place, or null. */
    private static ParserRuleContext expressionAt(final ParseTree node, final int line, final int column) {
        if (node instanceof FrostlakeParser.BooleanExprContext || node instanceof FrostlakeParser.ExpressionContext) {
            final Token start = ((ParserRuleContext) node).getStart();
            if (start != null && start.getLine() == line && start.getCharPositionInLine() == column) {
                return (ParserRuleContext) node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParserRuleContext found = expressionAt(node.getChild(i), line, column);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The scalar subquery the error came from: the expression's only one, or the one whose text holds a
     * token at the place the error names. Null when none can be told.
     */
    private static FrostlakeParser.SelectStatementContext failingQuery(final ParserRuleContext expression,
                                                                       final String message) {
        final List<FrostlakeParser.SelectStatementContext> queries = new ArrayList<>();
        collectScalarSubqueries(expression, queries);
        if (queries.size() == 1) {
            return queries.get(0);
        }
        for (final FrostlakeParser.SelectStatementContext query : queries) {
            final int[] place = ScriptedErrorPlace.placeOf(ScriptedErrorPlace.inEnclosingText(message,
                query.getStart().getLine(), query.getStart().getCharPositionInLine()));
            if (place != null && startsATokenAt(query, place[0], place[1])) {
                return query;
            }
        }
        return null;
    }

    /**
     * The subqueries of an expression, outermost only: a nested one is part of its enclosing text. An EXISTS,
     * an IN and a quantified comparison place their subquery's errors in the expression as a scalar subquery
     * does: {@code LET a BOOLEAN := EXISTS (SELECT missing …)} reports position 30, and {@code LET a BOOLEAN :=
     * 1 = ANY (SELECT missing …)} position 31 (live-verified).
     */
    private static void collectScalarSubqueries(final ParseTree node,
                                                final List<FrostlakeParser.SelectStatementContext> queries) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            queries.add(((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement());
            return;
        }
        if (node instanceof FrostlakeParser.ExistsExprContext) {
            queries.add(((FrostlakeParser.ExistsExprContext) node).selectStatement());
            return;
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            final FrostlakeParser.InSubqueryExprContext in = (FrostlakeParser.InSubqueryExprContext) node;
            collectScalarSubqueries(in.expression(), queries);
            queries.add(in.selectStatement());
            return;
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            final FrostlakeParser.QuantifiedComparisonExprContext quantified =
                (FrostlakeParser.QuantifiedComparisonExprContext) node;
            collectScalarSubqueries(quantified.expression(), queries);
            queries.add(quantified.selectStatement());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectScalarSubqueries(node.getChild(i), queries);
        }
    }

    private static boolean startsATokenAt(final ParseTree node, final int line, final int column) {
        if (node instanceof TerminalNode) {
            final Token token = ((TerminalNode) node).getSymbol();
            return token.getLine() == line && token.getCharPositionInLine() == column;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (startsATokenAt(node.getChild(i), line, column)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the expression is the value a LET, an assignment or a DECLARE default gives a variable. */
    private static boolean assignsAValue(final ParserRuleContext expression) {
        ParseTree holder = expression.getParent();
        while (holder instanceof FrostlakeParser.BooleanExprContext || holder instanceof FrostlakeParser.ExpressionContext) {
            holder = holder.getParent();
        }
        return holder instanceof FrostlakeParser.LetStatementContext
            || holder instanceof FrostlakeParser.AssignmentStatementContext
            || holder instanceof FrostlakeParser.DeclarationItemContext
            || holder instanceof FrostlakeParser.UntypedDeclarationItemContext;
    }
}
