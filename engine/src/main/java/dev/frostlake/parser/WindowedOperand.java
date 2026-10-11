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
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlIdentifiers;
import java.math.BigDecimal;
import java.util.Locale;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * OVER written after what is no call of a function. The account reads the window, and refuses what stands before
 * it while the statement compiles (all live-verified). A value that can be no function - a column, a literal, a
 * bind, a query, a call that already has its window - is refused first, before a table or a function of the
 * statement is resolved, and told as the account spells it:
 *
 * <pre>
 *   SELECT x OVER () FROM t                    invalid function 'X'
 *   SELECT t.x OVER () FROM t                  invalid function 'T.X'
 *   SELECT "x" OVER () FROM t                  invalid function '"x"'
 *   SELECT 1.50 OVER ()                        invalid function '1.5'
 *   SELECT 'a' OVER ()                         invalid function ''a''
 *   SELECT NULL OVER ()                        invalid function 'null'
 *   SELECT SUM(x) OVER () OVER () FROM t       invalid function 'SUM(X) OVER ()'
 * </pre>
 *
 * <p>Anything else is an operation the account names as a function, refused as a call of one that is no window
 * function is:
 *
 * <pre>
 *   SELECT x::INT OVER () FROM t               Invalid function type [CAST] for window function.
 *   SELECT CASE WHEN TRUE THEN 1 END OVER ()   Invalid function type [CASE] for window function.
 *   SELECT [1] OVER ()                         Invalid function type [ARRAY_CONSTRUCT] for window function.
 *   SELECT v:a OVER () FROM t                  Invalid function type [GET] for window function.
 *   SELECT (x + 1) OVER () FROM t              Invalid function type [+] for window function.
 *   SELECT (-x) OVER () FROM t                 Invalid function type [NEGATE] for window function.
 *   SELECT (x IS NULL) OVER () FROM t          Invalid function type [IS NULL] for window function.
 *   SELECT DATE '2020-01-01' OVER ()           Invalid function type [TO_DATE] for window function.
 * </pre>
 *
 * <p>OVER binds tighter than a sign and every operator beside it, so {@code x + 1 OVER ()} refuses the 1 and
 * {@code -x OVER ()} the x, and looser than a cast or a path.
 */
public final class WindowedOperand {

    private WindowedOperand() {
    }

    /**
     * The refusal a node makes: of the value its OVER follows, or of the method call written with OVER.
     *
     * @param node a node of the parsed statement
     * @return the refusal, or null when the node makes none
     */
    static OperandRefusal of(final ParseTree node) {
        if (node instanceof FrostlakeParser.InstanceMethodCallExprContext) {
            final FrostlakeParser.InstanceMethodCallExprContext call =
                (FrostlakeParser.InstanceMethodCallExprContext) node;
            if (call.overClause() == null) {
                return null;
            }
            return new OperandRefusal(kindSentence(methodName(call)),
                call.overClause().OVER().getSymbol().getTokenIndex(), false);
        }
        if (!(node instanceof FrostlakeParser.WindowedOperandExprContext)) {
            return null;
        }
        final FrostlakeParser.WindowedOperandExprContext windowed = (FrostlakeParser.WindowedOperandExprContext) node;
        final int order = windowed.overClause().OVER().getSymbol().getTokenIndex();
        if (windowed.expression() instanceof FrostlakeParser.ExistsExprContext
                || windowed.expression() instanceof FrostlakeParser.IsNullExprContext) {
            // Neither takes a window: the OVER after it reads as a name, and its parenthesis is refused.
            final Token open = windowed.overClause().LPAREN().getSymbol();
            final int[] shown = LeadingCommentOffset.rebase(open.getLine(), open.getCharPositionInLine());
            return new OperandRefusal("syntax error line " + shown[0] + " at position " + shown[1] + " unexpected '"
                + open.getText() + "'.", order, true);
        }
        final ParseTree value = unwrapped(windowed.expression());
        final String spelled = valueSpelling(value);
        if (spelled != null) {
            return new OperandRefusal("invalid function '" + spelled + "'", order, true);
        }
        return new OperandRefusal(kindSentence(functionOf(value)), order, false);
    }

    private static String kindSentence(final String function) {
        return "Invalid function type [" + function + "] for window function.";
    }

    /** What parentheses hold, however many of them. */
    private static ParseTree unwrapped(final ParseTree operand) {
        ParseTree value = operand;
        while (value instanceof FrostlakeParser.ParenExprContext || value instanceof FrostlakeParser.ValueExprContext) {
            value = value instanceof FrostlakeParser.ParenExprContext
                ? ((FrostlakeParser.ParenExprContext) value).booleanExpr()
                : ((FrostlakeParser.ValueExprContext) value).expression();
        }
        return value;
    }

    /** A class instance's method as the account names it: the instance, a '!', the method. */
    static String methodName(final FrostlakeParser.InstanceMethodCallExprContext call) {
        final StringBuilder name = new StringBuilder();
        for (final String part : ParseTreeText.qualifiedNameParts(call.qualifiedName())) {
            name.append(name.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
        }
        return name.append('!').append(SqlIdentifiers.spellCanonical(ParseTreeText.getIdentifier(call.identifier())))
            .toString();
    }

    /**
     * How the account spells a value that can be no function, or null for an operation it names as one: a name
     * folded as an identifier is, a number in its plainest form, a text in quotes, NULL in lower case.
     */
    private static String valueSpelling(final ParseTree value) {
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            if (value.getText().startsWith("$")) {
                // A column told by its place, $1, is told as written.
                return value.getText();
            }
            final StringBuilder name = new StringBuilder();
            for (final String part : ParseTreeText.qualifiedNameParts(
                    ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName())) {
                name.append(name.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
            }
            return name.toString();
        }
        if (value instanceof FrostlakeParser.LiteralExprContext) {
            return literalSpelling(((FrostlakeParser.LiteralExprContext) value).literal());
        }
        if (value instanceof FrostlakeParser.BindVarExprContext
                || value instanceof FrostlakeParser.PositionalBindExprContext
                || value instanceof FrostlakeParser.SessionVarExprContext) {
            return value.getText();
        }
        if (value instanceof FrostlakeParser.ScalarSubqueryExprContext
                || value instanceof FrostlakeParser.ExistsExprContext) {
            return QuantifiedComparisonOperand.written((ParserRuleContext) value);
        }
        if (value instanceof FrostlakeParser.WindowedOperandExprContext || hasWindow(value)) {
            return upperOutsideQuotes(QuantifiedComparisonOperand.written((ParserRuleContext) value));
        }
        return null;
    }

    private static String literalSpelling(final FrostlakeParser.LiteralContext literal) {
        final String text = literal.getText();
        if (literal.NULL() != null) {
            return "null";
        }
        if (literal.TRUE() != null || literal.FALSE() != null) {
            return text.toUpperCase(Locale.ROOT);
        }
        if (literal.DOLLAR_QUOTED_STRING() != null) {
            return "'" + text.substring(2, text.length() - 2).replace("'", "''") + "'";
        }
        if (literal.INTEGER_LITERAL() != null || literal.FLOAT_LITERAL() != null) {
            try {
                final BigDecimal number = new BigDecimal(text);
                return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
            } catch (final NumberFormatException unread) {
                return text;
            }
        }
        return text;
    }

    /** A text in upper case, what it holds in quotes left as written. */
    private static String upperOutsideQuotes(final String text) {
        final StringBuilder out = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quote == 0 && (c == '\'' || c == '"')) {
                quote = c;
            } else if (quote != 0 && c == quote) {
                quote = 0;
            }
            out.append(quote == 0 ? Character.toUpperCase(c) : c);
        }
        return out.toString();
    }

    /** Whether a value is a call written with its own window. */
    private static boolean hasWindow(final ParseTree value) {
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

    /** The function the account names an operation as. */
    private static String functionOf(final ParseTree value) {
        if (value instanceof FrostlakeParser.InstanceMethodCallExprContext) {
            return methodName((FrostlakeParser.InstanceMethodCallExprContext) value);
        }
        if (value instanceof FrostlakeParser.CastExprContext || value instanceof FrostlakeParser.CastExpr2Context) {
            return "CAST";
        }
        if (value instanceof FrostlakeParser.TryCastExprContext) {
            return "TRY_CAST";
        }
        if (value instanceof FrostlakeParser.JsonArrayExprContext) {
            return "ARRAY_CONSTRUCT";
        }
        if (value instanceof FrostlakeParser.JsonObjectExprContext) {
            return "OBJECT_CONSTRUCT";
        }
        if (value instanceof FrostlakeParser.ObjectAccessExprContext
                || value instanceof FrostlakeParser.ArrayAccessExprContext
                || value instanceof FrostlakeParser.FieldAccessExprContext) {
            return "GET";
        }
        if (value instanceof FrostlakeParser.TypedDateTimeLiteralExprContext) {
            return "TO_" + ((FrostlakeParser.TypedDateTimeLiteralExprContext) value).dateTimeLiteralType().getText()
                .toUpperCase(Locale.ROOT);
        }
        if (value instanceof FrostlakeParser.IntervalExprContext
                || value instanceof FrostlakeParser.IntervalStringExprContext) {
            return "INTERVAL_LITERAL";
        }
        if (value instanceof FrostlakeParser.CollateExprContext
                || value instanceof FrostlakeParser.CollateFuncExprContext) {
            return "COLLATE";
        }
        final String operator = operatorOf(value);
        if (operator != null) {
            return operator;
        }
        // A call by its function, a CASE and a niladic function by their word.
        return ((ParserRuleContext) value).getStart().getText().toUpperCase(Locale.ROOT);
    }

    /** The name the account gives an operator applied in parentheses, or null for what is none. */
    private static String operatorOf(final ParseTree value) {
        if (value instanceof FrostlakeParser.ConcatExprContext) {
            return "||";
        }
        if (value instanceof FrostlakeParser.AdditiveExprContext) {
            return ((FrostlakeParser.AdditiveExprContext) value).op.getText();
        }
        if (value instanceof FrostlakeParser.MultiplicativeExprContext) {
            return ((FrostlakeParser.MultiplicativeExprContext) value).op.getText();
        }
        if (value instanceof FrostlakeParser.ComparisonExprContext) {
            return ((FrostlakeParser.ComparisonExprContext) value).op.getText();
        }
        if (value instanceof FrostlakeParser.UnaryExprContext) {
            final Token sign = ((FrostlakeParser.UnaryExprContext) value).op;
            return sign.getType() == FrostlakeParser.MINUS ? "NEGATE" : "UNARY PLUS";
        }
        if (value instanceof FrostlakeParser.AndExprContext) {
            return "AND";
        }
        if (value instanceof FrostlakeParser.OrExprContext) {
            return "OR";
        }
        if (value instanceof FrostlakeParser.NotExprContext) {
            return "NOT";
        }
        if (value instanceof FrostlakeParser.IsNullExprContext) {
            return ((FrostlakeParser.IsNullExprContext) value).NOT() != null ? "IS NOT NULL" : "IS NULL";
        }
        if (value instanceof FrostlakeParser.IsDistinctExprContext) {
            // IS DISTINCT FROM is the negation of a null-safe equality.
            return ((FrostlakeParser.IsDistinctExprContext) value).NOT() != null ? "EQUAL_NULL" : "NOT";
        }
        if (value instanceof FrostlakeParser.InListExprContext) {
            return ((FrostlakeParser.InListExprContext) value).NOT() != null ? "NOT" : "IN";
        }
        if (value instanceof FrostlakeParser.InSubqueryExprContext) {
            return ((FrostlakeParser.InSubqueryExprContext) value).NOT() != null ? "NOT" : "IN";
        }
        if (value instanceof FrostlakeParser.LikeExprContext) {
            final FrostlakeParser.LikeExprContext like = (FrostlakeParser.LikeExprContext) value;
            return like.NOT() != null ? "NOT" : like.ILIKE() != null ? "ILIKE" : "LIKE";
        }
        if (value instanceof FrostlakeParser.RlikeExprContext) {
            return ((FrostlakeParser.RlikeExprContext) value).NOT() != null ? "NOT" : "RLIKE";
        }
        if (value instanceof FrostlakeParser.BetweenExprContext) {
            return ((FrostlakeParser.BetweenExprContext) value).NOT() != null ? "NOT" : "BETWEEN";
        }
        return null;
    }
}
