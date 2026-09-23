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

import dev.frostlake.functions.scalar.context.LastQueryId;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;

/**
 * RESULT_SCAN's argument as the statement compiles it, read from the parse tree (live-verified):
 *
 * <pre>
 *   'id'  $$id$$  ('id')  $v  :v  (SELECT …)  LAST_QUERY_ID(…)    read as a value: the ID, or an index
 *   1  -2  (3)  -(-3)  1.0  2E0                                   a whole number: LAST_QUERY_ID(n)'s statement
 *   10001  -10001  1E5                                            Value for parameter 1 exceeds maximum …
 *   NULL  TRUE  FALSE  1.5  -1.5  X'AB'                           argument needs to be a string: '&lt;line&gt;'
 *   UPPER('x')  NULL::VARCHAR  'a' || ''  +1  -(ABS(1))  CASE …   argument &lt;line&gt; to function &lt;column&gt;
 *                                                                 needs to be constant, found '&lt;token&gt;'
 *   DATE '2020-01-01'                                             … found 'TOK_ANSI_LITERAL'
 *   t.c                                                           error line … Invalid result query ID, found 't'
 * </pre>
 *
 * <p>The "needs to be constant" sentence carries the line and column of the argument's top token where
 * other such sentences carry the argument's ordinal and the function's name: the operator of an operator
 * form, the first word of a call or a keyword form, and the whole text of an array or object literal.
 */
final class ResultScanArgument {

    private ResultScanArgument() {
    }

    /**
     * The LAST_QUERY_ID index a whole-number argument names, or null for an argument read as a value;
     * every other shape is refused as live refuses it.
     *
     * @param argument the call's first argument
     * @return the index, or null
     */
    static Integer index(final FrostlakeParser.FunctionArgContext argument) {
        if (argument.booleanExpr() == null) {
            return null;
        }
        return indexOf(argument.booleanExpr());
    }

    /**
     * The index a number read as a value names: a whole number within the limit, while a fraction is
     * refused as not a string.
     *
     * @param value the number the argument answered
     * @param argument the argument, whose line the refusal names
     * @return the index
     */
    static int indexOf(final Number value, final FrostlakeParser.FunctionArgContext argument) {
        final BigDecimal number = new BigDecimal(value.toString());
        if (number.stripTrailingZeros().scale() > 0) {
            throw notAString(argument.getStart());
        }
        LastQueryId.requireWithinLimit(number);
        return number.intValue();
    }

    private static Integer indexOf(final FrostlakeParser.BooleanExprContext argument) {
        if (argument instanceof FrostlakeParser.ValueExprContext) {
            return indexOf(((FrostlakeParser.ValueExprContext) argument).expression());
        }
        throw notConstant(topToken(argument));
    }

    private static Integer indexOf(final FrostlakeParser.ExpressionContext argument) {
        if (argument instanceof FrostlakeParser.ParenExprContext) {
            return indexOf(((FrostlakeParser.ParenExprContext) argument).booleanExpr());
        }
        if (argument instanceof FrostlakeParser.LiteralExprContext) {
            final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) argument).literal();
            if (literal.STRING_LITERAL() != null || literal.DOLLAR_QUOTED_STRING() != null) {
                return null;
            }
            if (literal.INTEGER_LITERAL() == null && literal.FLOAT_LITERAL() == null) {
                throw notAString(argument.getStart());
            }
            return wholeIndex(new BigDecimal(literal.getText()), argument.getStart());
        }
        if (argument instanceof FrostlakeParser.UnaryExprContext) {
            final FrostlakeParser.UnaryExprContext unary = (FrostlakeParser.UnaryExprContext) argument;
            if (unary.op.getType() == FrostlakeParser.MINUS) {
                final BigDecimal negated = negatedNumber(unary);
                if (negated != null) {
                    return wholeIndex(negated, argument.getStart());
                }
            }
            throw notConstant(unary.op);
        }
        if (argument instanceof FrostlakeParser.SessionVarExprContext
                || argument instanceof FrostlakeParser.BindVarExprContext
                || argument instanceof FrostlakeParser.ScalarSubqueryExprContext
                || argument instanceof FrostlakeParser.FunctionCallExprContext
                    && "LAST_QUERY_ID".equalsIgnoreCase(
                        ((FrostlakeParser.FunctionCallExprContext) argument).functionName().getText())) {
            return null;
        }
        if (argument instanceof FrostlakeParser.TypedDateTimeLiteralExprContext) {
            throw new RuntimeException(SqlCompilationError.of(
                "argument 0 to function -1 needs to be constant, found 'TOK_ANSI_LITERAL'"));
        }
        if (argument instanceof FrostlakeParser.QualifiedNameExprContext) {
            final Token name = argument.getStart();
            throw new RuntimeException(SqlCompilationError.at(name.getLine(), name.getCharPositionInLine(),
                "Invalid result query ID, found '" + name.getText() + "'"));
        }
        if (argument instanceof FrostlakeParser.JsonArrayExprContext
                || argument instanceof FrostlakeParser.JsonObjectExprContext) {
            throw notConstant(argument.getStart(), writtenText(argument));
        }
        throw notConstant(topToken(argument));
    }

    /**
     * The number a unary minus over a number literal folds to — through parentheses, and over a
     * parenthesised minus — or null when its operand is anything else. A minus written directly before
     * another minus is no function the account knows.
     */
    private static BigDecimal negatedNumber(final FrostlakeParser.UnaryExprContext minus) {
        final FrostlakeParser.ExpressionContext operand = minus.expression();
        if (operand instanceof FrostlakeParser.UnaryExprContext
                && ((FrostlakeParser.UnaryExprContext) operand).op.getType() == FrostlakeParser.MINUS) {
            throw new RuntimeException(SqlCompilationError.of("invalid function '-'"));
        }
        final FrostlakeParser.ExpressionContext inner = unparenthesised(operand);
        if (inner instanceof FrostlakeParser.LiteralExprContext) {
            final FrostlakeParser.LiteralContext literal = ((FrostlakeParser.LiteralExprContext) inner).literal();
            if (literal.INTEGER_LITERAL() != null || literal.FLOAT_LITERAL() != null) {
                return new BigDecimal(literal.getText()).negate();
            }
            return null;
        }
        if (inner instanceof FrostlakeParser.UnaryExprContext
                && ((FrostlakeParser.UnaryExprContext) inner).op.getType() == FrostlakeParser.MINUS) {
            final BigDecimal nested = negatedNumber((FrostlakeParser.UnaryExprContext) inner);
            return nested == null ? null : nested.negate();
        }
        return null;
    }

    /** The expression inside any number of parentheses, or null when a parenthesis holds no plain value. */
    private static FrostlakeParser.ExpressionContext unparenthesised(final FrostlakeParser.ExpressionContext expression) {
        FrostlakeParser.ExpressionContext inner = expression;
        while (inner instanceof FrostlakeParser.ParenExprContext) {
            final FrostlakeParser.BooleanExprContext held = ((FrostlakeParser.ParenExprContext) inner).booleanExpr();
            if (!(held instanceof FrostlakeParser.ValueExprContext)) {
                return null;
            }
            inner = ((FrostlakeParser.ValueExprContext) held).expression();
        }
        return inner;
    }

    private static Integer wholeIndex(final BigDecimal number, final Token at) {
        if (number.stripTrailingZeros().scale() > 0) {
            throw notAString(at);
        }
        LastQueryId.requireWithinLimit(number);
        return Integer.valueOf(number.intValue());
    }

    /** The operator of an operator form, or the first word of anything else. */
    private static Token topToken(final ParserRuleContext form) {
        if (form.getChildCount() > 1 && (form.getChild(0) instanceof FrostlakeParser.ExpressionContext
                || form.getChild(0) instanceof FrostlakeParser.BooleanExprContext)) {
            for (int i = 1; i < form.getChildCount(); i++) {
                if (form.getChild(i) instanceof TerminalNode) {
                    return ((TerminalNode) form.getChild(i)).getSymbol();
                }
            }
        }
        return form.getStart();
    }

    private static String writtenText(final ParserRuleContext form) {
        return form.getStart().getInputStream().getText(
            Interval.of(form.getStart().getStartIndex(), form.getStop().getStopIndex()));
    }

    private static RuntimeException notConstant(final Token at) {
        return notConstant(at, at.getText());
    }

    private static RuntimeException notConstant(final Token at, final String found) {
        final int[] shown = LeadingCommentOffset.rebase(at.getLine(), at.getCharPositionInLine());
        return new RuntimeException(SqlCompilationError.of("argument " + shown[0] + " to function " + shown[1]
            + " needs to be constant, found '" + found + "'"));
    }

    private static RuntimeException notAString(final Token at) {
        final int[] shown = LeadingCommentOffset.rebase(at.getLine(), at.getCharPositionInLine());
        return new RuntimeException(SqlCompilationError.of("argument needs to be a string: '" + shown[0] + "'"));
    }
}
