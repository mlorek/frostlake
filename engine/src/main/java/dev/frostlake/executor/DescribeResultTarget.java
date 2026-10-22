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
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.functions.scalar.context.LastQueryId;
import dev.frostlake.parser.FrostlakeParser;

import java.math.BigDecimal;

import org.antlr.v4.runtime.Token;

/**
 * The statement a DESCRIBE RESULT names (all live-verified). The account reads an expression there and takes the
 * query ID of a constant one: a text is the ID itself, a number, negated or not, the statement LAST_QUERY_ID of it
 * names, a session variable the ID its value spells, and a call of LAST_QUERY_ID the statement it names, its
 * argument judged as the function judges it; parentheses around any of them are read through. Anything else is
 * refused while the statement compiles:
 *
 * <pre>
 *   DESCRIBE RESULT x             error line 1 at position 16 Invalid result query ID, found 'x'
 *   DESCRIBE RESULT NULL          argument needs to be a string: '1'
 *   DESCRIBE RESULT UPPER('x')    argument 1 to function 16 needs to be constant, found 'UPPER'
 *   DESCRIBE RESULT 'a' || 'b'    argument 1 to function 20 needs to be constant, found '||'
 * </pre>
 *
 * The number the last sentence names is where the expression's head stands in its line: a call's name, an
 * operator's sign.
 */
final class DescribeResultTarget {

    private DescribeResultTarget() {
    }

    /**
     * The query ID a DESCRIBE RESULT names, or null where it names none: a LAST_QUERY_ID past the session's
     * statements, a session variable holding NULL.
     *
     * @param target   the expression after RESULT
     * @param executor the executor, whose session the ID is read from
     * @return the query ID, or null
     */
    static String queryId(final FrostlakeParser.ExpressionContext target, final QueryExecutor executor) {
        final FrostlakeParser.ExpressionContext read = throughParentheses(target);
        if (read instanceof FrostlakeParser.LiteralExprContext) {
            final Object value = evaluated(read, executor);
            if (value instanceof String) {
                return (String) value;
            }
            if (!(value instanceof Number)) {
                throw new RuntimeException(SqlCompilationError.of("argument needs to be a string: '1'"));
            }
            return statementAt((Number) value, executor);
        }
        if (read instanceof FrostlakeParser.UnaryExprContext
                && throughParentheses(((FrostlakeParser.UnaryExprContext) read).expression())
                    instanceof FrostlakeParser.LiteralExprContext) {
            final Object value = evaluated(read, executor);
            return value instanceof Number ? statementAt((Number) value, executor) : null;
        }
        if (callsLastQueryId(read)) {
            requireOneIndex(read, executor);
        }
        if (read instanceof FrostlakeParser.SessionVarExprContext || callsLastQueryId(read)) {
            final Object value = evaluated(read, executor);
            return value == null ? null : String.valueOf(value);
        }
        if (read instanceof FrostlakeParser.QualifiedNameExprContext) {
            final Token name = read.getStart();
            throw new RuntimeException(SqlCompilationError.at(name.getLine(), name.getCharPositionInLine(),
                "Invalid result query ID, found '" + ParseTreeText.getOriginalText(read) + "'"));
        }
        final Token head = head(read);
        throw new RuntimeException(SqlCompilationError.of("argument 1 to function " + head.getCharPositionInLine()
            + " needs to be constant, found '" + head.getText() + "'"));
    }

    /** An expression with the parentheses written around it read through. */
    private static FrostlakeParser.ExpressionContext throughParentheses(final FrostlakeParser.ExpressionContext
                                                                            expression) {
        FrostlakeParser.ExpressionContext read = expression;
        while (read instanceof FrostlakeParser.ParenExprContext) {
            final FrostlakeParser.BooleanExprContext inside = ((FrostlakeParser.ParenExprContext) read).booleanExpr();
            if (!(inside instanceof FrostlakeParser.ValueExprContext)) {
                break;
            }
            read = ((FrostlakeParser.ValueExprContext) inside).expression();
        }
        return read;
    }

    /** Whether an expression is a call of LAST_QUERY_ID. */
    private static boolean callsLastQueryId(final FrostlakeParser.ExpressionContext expression) {
        if (!(expression instanceof FrostlakeParser.FunctionCallExprContext)) {
            return false;
        }
        final Expression parsed = new ExpressionAstBuilder().visit(expression);
        return parsed instanceof FunctionCallExpression
            && "LAST_QUERY_ID".equalsIgnoreCase(((FunctionCallExpression) parsed).getFunctionName());
    }

    /**
     * Refuses a LAST_QUERY_ID call of more than one argument by the types written, where the call stands:
     * {@code Invalid argument types for function 'LAST_QUERY_ID': (NUMBER(1,0), NUMBER(1,0))}.
     */
    private static void requireOneIndex(final FrostlakeParser.ExpressionContext call, final QueryExecutor executor) {
        final FunctionCallExpression parsed = (FunctionCallExpression) new ExpressionAstBuilder().visit(call);
        if (parsed.getArguments().size() < 2) {
            return;
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        final StringBuilder types = new StringBuilder();
        for (final Expression argument : parsed.getArguments()) {
            types.append(types.length() == 0 ? "" : ", ").append(evaluator.argumentTypeText(argument));
        }
        final Token at = call.getStart();
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "Invalid argument types for function 'LAST_QUERY_ID': (" + types + ")"));
    }

    /** The statement LAST_QUERY_ID names by this index. */
    private static String statementAt(final Number index, final QueryExecutor executor) {
        final BigDecimal whole = LastQueryId.wholeIndex(index);
        LastQueryId.requireWithinLimit(whole);
        return executor.getResultCache().getQueryId(whole.intValue());
    }

    /** A constant expression's value, judged and computed as a select item's is. */
    private static Object evaluated(final FrostlakeParser.ExpressionContext expression, final QueryExecutor executor) {
        final Expression parsed = new ExpressionAstBuilder().visit(expression);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        evaluator.validateStrict(parsed);
        return evaluator.evaluate(parsed, null);
    }

    /** The token an expression's refusal is placed at: an operator's sign, else its first token. */
    private static Token head(final FrostlakeParser.ExpressionContext expression) {
        if (expression instanceof FrostlakeParser.AdditiveExprContext) {
            return ((FrostlakeParser.AdditiveExprContext) expression).op;
        }
        if (expression instanceof FrostlakeParser.MultiplicativeExprContext) {
            return ((FrostlakeParser.MultiplicativeExprContext) expression).op;
        }
        if (expression instanceof FrostlakeParser.ComparisonExprContext) {
            return ((FrostlakeParser.ComparisonExprContext) expression).op;
        }
        if (expression instanceof FrostlakeParser.CastExpr2Context) {
            return ((FrostlakeParser.CastExpr2Context) expression).DOUBLE_COLON().getSymbol();
        }
        return expression.getStart();
    }
}
