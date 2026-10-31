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

package dev.frostlake.parser;

import dev.frostlake.executor.LeadingCommentOffset;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * What stands after a quantifier where a query is taken. The account reads a value there - an operand as tight
 * as a sum, a concatenation, a cast, a path or an IS NULL - and refuses it while the statement compiles, before a
 * table or a column of the statement is resolved, naming the token the operand turns on (all live-verified):
 *
 * <pre>
 *   SELECT 1 = ANY 1                           Invalid query block: 1.
 *   SELECT 1 = ANY x FROM t                    Invalid query block: x.
 *   SELECT 1 = ANY t.x FROM t                  Invalid query block: t.
 *   SELECT 1 = ANY ABS(1)                      Invalid query block: ABS.
 *   SELECT 1 = ANY 1 + 2                       Invalid query block: +.
 *   SELECT 1 = ANY (1, 2)                      Invalid query block: (.
 *   SELECT 1 = ANY [1, 2]                      Invalid query block: [1, 2].
 *   SELECT 1 = ANY DATE '2020-01-01'           Invalid query block: TOK_ANSI_LITERAL.
 *   SELECT 1 = ANY (SELECT 1) || ''            Invalid query block: ||.
 *   SELECT 1 = ANY (SELECT 1)::VARCHAR         Invalid query block: ::.
 *   SELECT 1 = ANY (SELECT 1) IS NULL          Invalid query block: IS.
 *   SELECT 1 = ANY (SELECT 1) COLLATE 'de'     Invalid query block: COLLATE.
 *   SELECT 1 = ANY (SELECT 1) :x               Invalid query block: :.
 *   SELECT 1 = ANY (SELECT 1) [0]              Invalid query block: [.
 * </pre>
 *
 * <p>A name is told by its first part and a call by its function's, both as written; a literal as written; an
 * array or an object by its whole text; parentheses by what they hold, and a list of values by its opening
 * parenthesis. The same holds in a WHERE, an ORDER BY, a CASE and a call's argument. A comparison-level operator
 * after the operand - {@code = TRUE}, {@code IN (TRUE)}, {@code LIKE 'a'} - is no part of it: after a query it
 * reads as written, and after a value the value is still what is refused.
 */
public final class QuantifiedComparisonOperand {

    private static final String ANSI_LITERAL = "TOK_ANSI_LITERAL";

    private QuantifiedComparisonOperand() {
    }

    /**
     * The refusal a node makes: of the value written after its quantifier, or of the value operator it applies to
     * a quantified comparison over a query.
     *
     * @param node a node of the parsed statement
     * @return the refusal, or null when the node makes none
     */
    static OperandRefusal of(final ParseTree node) {
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext && !quantified(node)) {
            final FrostlakeParser.QuantifiedComparisonExprContext comparison =
                (FrostlakeParser.QuantifiedComparisonExprContext) node;
            return new OperandRefusal(
                comparison.operand != null ? sentence(comparison.operand) : "Invalid query block: (.",
                comparison.quantifier().getStart().getTokenIndex(), true);
        }
        final Token refused = refusedOperator(node);
        if (refused == null) {
            return null;
        }
        return new OperandRefusal("Invalid query block: " + spelling(node, refused) + ".", refused.getTokenIndex(),
            true);
    }

    /** The sentence for a value after a quantifier: the token it turns on, or the syntax error an EXISTS is. */
    private static String sentence(final ParseTree operand) {
        ParseTree value = operand;
        // The operators the account reads no tighter than the comparison end the operand before them.
        while (comparisonLevelLeft(value) != null) {
            value = comparisonLevelLeft(value);
        }
        if (value instanceof FrostlakeParser.ExistsExprContext) {
            // The EXISTS, then the parenthesis after it read on: its first token, and the parenthesis closing it
            // after a query that reads a FROM - EXISTS (SELECT 1) is 'EXISTS' and 'SELECT', EXISTS (SELECT 1 FROM t)
            // the ')' too, EXISTS ((SELECT 1)) the inner '(' (live-verified).
            final FrostlakeParser.ExistsExprContext exists = (FrostlakeParser.ExistsExprContext) value;
            final FrostlakeParser.SelectStatementContext query = exists.selectStatement();
            final StringBuilder lines = new StringBuilder(syntaxLine(exists.EXISTS().getSymbol()));
            lines.append('\n').append(syntaxLine(query.getStart()));
            if (query.selectOperand(0).selectClause() != null && query.selectOperand(0).selectClause().FROM() != null
                    && exists.RPAREN() != null) {
                lines.append('\n').append(syntaxLine(exists.RPAREN().getSymbol()));
            }
            return lines.toString();
        }
        return "Invalid query block: " + root(value) + ".";
    }

    /** A syntax error's line naming a token where it was written. */
    private static String syntaxLine(final Token token) {
        final int[] shown = LeadingCommentOffset.rebase(token.getLine(), token.getCharPositionInLine());
        return "syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '" + token.getText() + "'.";
    }

    /** The operand a comparison-level operator applies to, or null when a node is none. */
    private static ParseTree comparisonLevelLeft(final ParseTree node) {
        if (node instanceof FrostlakeParser.ComparisonExprContext) {
            return ((FrostlakeParser.ComparisonExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.QuantifiedComparisonExprContext) {
            return ((FrostlakeParser.QuantifiedComparisonExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.LikeExprContext) {
            return ((FrostlakeParser.LikeExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.LikeAnyAllExprContext) {
            return ((FrostlakeParser.LikeAnyAllExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.LikeAnyAllSingleExprContext) {
            return ((FrostlakeParser.LikeAnyAllSingleExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.RlikeExprContext) {
            return ((FrostlakeParser.RlikeExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.BetweenExprContext) {
            return ((FrostlakeParser.BetweenExprContext) node).expression(0);
        }
        if (node instanceof FrostlakeParser.InListExprContext) {
            return ((FrostlakeParser.InListExprContext) node).expression();
        }
        if (node instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) node).expression();
        }
        return null;
    }

    /** The token a value turns on, as the account names it. */
    private static String root(final ParseTree operand) {
        ParseTree value = operand;
        while (value instanceof FrostlakeParser.ParenExprContext || value instanceof FrostlakeParser.ValueExprContext) {
            value = value instanceof FrostlakeParser.ParenExprContext
                ? ((FrostlakeParser.ParenExprContext) value).booleanExpr()
                : ((FrostlakeParser.ValueExprContext) value).expression();
        }
        if (value instanceof FrostlakeParser.TypedDateTimeLiteralExprContext
                || value instanceof FrostlakeParser.IntervalExprContext
                || value instanceof FrostlakeParser.IntervalStringExprContext
                || value instanceof FrostlakeParser.UnknownTypedLiteralExprContext) {
            return ANSI_LITERAL;
        }
        if (value instanceof FrostlakeParser.JsonArrayExprContext
                || value instanceof FrostlakeParser.JsonObjectExprContext) {
            return written((ParserRuleContext) value);
        }
        if (value instanceof FrostlakeParser.BindVarExprContext) {
            return ((FrostlakeParser.BindVarExprContext) value).COLON().getText();
        }
        final Token operator = operatorOf(value);
        if (operator != null) {
            return operator.getText();
        }
        if (isWindowed(value)) {
            return "OVER";
        }
        // A name by its first part, a call by its function's, a CASE by its word, a literal whole: the first token.
        return value instanceof ParserRuleContext ? ((ParserRuleContext) value).getStart().getText() : value.getText();
    }

    /** The token of the operator a value is an application of, or null when it is none. */
    private static Token operatorOf(final ParseTree value) {
        if (value instanceof FrostlakeParser.AdditiveExprContext) {
            return ((FrostlakeParser.AdditiveExprContext) value).op;
        }
        if (value instanceof FrostlakeParser.MultiplicativeExprContext) {
            return ((FrostlakeParser.MultiplicativeExprContext) value).op;
        }
        if (value instanceof FrostlakeParser.UnaryExprContext) {
            return ((FrostlakeParser.UnaryExprContext) value).op;
        }
        if (value instanceof FrostlakeParser.ComparisonExprContext) {
            return ((FrostlakeParser.ComparisonExprContext) value).op;
        }
        if (value instanceof FrostlakeParser.CastExpr2Context) {
            return ((FrostlakeParser.CastExpr2Context) value).DOUBLE_COLON().getSymbol();
        }
        if (value instanceof FrostlakeParser.CollateExprContext) {
            return ((FrostlakeParser.CollateExprContext) value).COLLATE().getSymbol();
        }
        if (value instanceof FrostlakeParser.IsNullExprContext) {
            return ((FrostlakeParser.IsNullExprContext) value).IS().getSymbol();
        }
        if (value instanceof FrostlakeParser.IsDistinctExprContext) {
            return ((FrostlakeParser.IsDistinctExprContext) value).IS().getSymbol();
        }
        if (value instanceof FrostlakeParser.ObjectAccessExprContext) {
            return ((FrostlakeParser.ObjectAccessExprContext) value).COLON(0).getSymbol();
        }
        if (value instanceof FrostlakeParser.ArrayAccessExprContext) {
            return ((FrostlakeParser.ArrayAccessExprContext) value).LBRACKET().getSymbol();
        }
        if (value instanceof FrostlakeParser.FieldAccessExprContext) {
            return ((FrostlakeParser.FieldAccessExprContext) value).DOT().getSymbol();
        }
        if (value instanceof FrostlakeParser.NotExprContext) {
            return ((FrostlakeParser.NotExprContext) value).NOT().getSymbol();
        }
        if (value instanceof FrostlakeParser.BangExprContext) {
            return ((FrostlakeParser.BangExprContext) value).BANG().getSymbol();
        }
        if (value instanceof FrostlakeParser.AndExprContext) {
            return ((FrostlakeParser.AndExprContext) value).AND().getSymbol();
        }
        if (value instanceof FrostlakeParser.OrExprContext) {
            return ((FrostlakeParser.OrExprContext) value).OR().getSymbol();
        }
        return null;
    }

    /** Whether a value is written with OVER: a windowed call, or a value a window follows. */
    private static boolean isWindowed(final ParseTree value) {
        if (value instanceof FrostlakeParser.WindowedOperandExprContext) {
            return true;
        }
        if (value instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) value).overClause() != null;
        }
        if (value instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) value).overClause() != null;
        }
        if (value instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) value).overClause() != null;
        }
        return false;
    }

    /** A node's own text, as it was written. */
    static String written(final ParserRuleContext node) {
        return node.getStart().getInputStream().getText(
            Interval.of(node.getStart().getStartIndex(), node.getStop().getStopIndex()));
    }

    /** The token of the operator {@code node} applies to a quantified comparison over a query, or null. */
    private static Token refusedOperator(final ParseTree node) {
        if (node instanceof FrostlakeParser.AdditiveExprContext) {
            final FrostlakeParser.AdditiveExprContext sum = (FrostlakeParser.AdditiveExprContext) node;
            return quantified(sum.expression(0)) ? sum.op : null;
        }
        if (node instanceof FrostlakeParser.MultiplicativeExprContext) {
            final FrostlakeParser.MultiplicativeExprContext product = (FrostlakeParser.MultiplicativeExprContext) node;
            return quantified(product.expression(0)) ? product.op : null;
        }
        if (node instanceof FrostlakeParser.CastExpr2Context) {
            final FrostlakeParser.CastExpr2Context cast = (FrostlakeParser.CastExpr2Context) node;
            return quantified(cast.expression()) ? cast.DOUBLE_COLON().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.CollateExprContext) {
            final FrostlakeParser.CollateExprContext collate = (FrostlakeParser.CollateExprContext) node;
            return quantified(collate.expression()) ? collate.COLLATE().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.IsNullExprContext) {
            final FrostlakeParser.IsNullExprContext test = (FrostlakeParser.IsNullExprContext) node;
            return quantified(test.expression()) ? test.IS().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.IsDistinctExprContext) {
            final FrostlakeParser.IsDistinctExprContext test = (FrostlakeParser.IsDistinctExprContext) node;
            return quantified(test.expression(0)) ? test.IS().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.ObjectAccessExprContext) {
            final FrostlakeParser.ObjectAccessExprContext path = (FrostlakeParser.ObjectAccessExprContext) node;
            return quantified(path.expression()) ? path.COLON(0).getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.ArrayAccessExprContext) {
            final FrostlakeParser.ArrayAccessExprContext subscript = (FrostlakeParser.ArrayAccessExprContext) node;
            return quantified(subscript.expression(0)) ? subscript.LBRACKET().getSymbol() : null;
        }
        if (node instanceof FrostlakeParser.OuterJoinOperandExprContext) {
            final FrostlakeParser.OuterJoinOperandExprContext marker = (FrostlakeParser.OuterJoinOperandExprContext) node;
            return quantified(marker.expression()) ? marker.PLUS().getSymbol() : null;
        }
        return null;
    }

    /** How live names the refused operator: as written, COLLATE and IS as their keywords. */
    private static String spelling(final ParseTree node, final Token operator) {
        if (node instanceof FrostlakeParser.CollateExprContext) {
            return "COLLATE";
        }
        if (node instanceof FrostlakeParser.IsNullExprContext || node instanceof FrostlakeParser.IsDistinctExprContext) {
            return "IS";
        }
        return operator.getText();
    }

    /** Whether an operand is a quantified comparison over a query, written without parentheses. */
    private static boolean quantified(final ParseTree operand) {
        return operand instanceof FrostlakeParser.QuantifiedComparisonExprContext
            && ((FrostlakeParser.QuantifiedComparisonExprContext) operand).selectStatement() != null;
    }
}
