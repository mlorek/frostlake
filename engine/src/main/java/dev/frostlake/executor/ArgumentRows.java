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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * A parenthesized list of values given to a named table-function parameter — {@code INPUT => ('a', 'b')} — which
 * the account reads as one ROW value. Only INFER_SCHEMA's FILES takes one. The others refuse it in their own
 * words:
 *
 * <pre>
 *   FLATTEN, INFER_SCHEMA, QUERY_HISTORY …   invalid type [ROW(VARCHAR(1), VARCHAR(1))] for parameter 'INPUT'
 *   GENERATOR                                argument 2 to function GENERATOR needs to be constant, found 'ROW(2, 'ab')'
 *   TO_QUERY                                 argument 0 to function -1 needs to be constant, found 'b'
 *   RESULT_SCAN                              error line 1 at position 44 / Invalid result query ID, found '('
 * </pre>
 *
 * <p>GENERATOR counts the argument among all the call's arguments, and prints the list as the plan holds it:
 * each constant folded to its value ({@code 1 + 1} is {@code 2}, {@code 1.50} is {@code 1.5}, {@code 1e2} is
 * {@code 100}, a NULL is {@code null}), a call as written. TO_QUERY names the parameter as written.
 */
final class ArgumentRows {

    private ArgumentRows() {
    }

    /**
     * Whether any named argument's value is a parenthesized list.
     *
     * @param named the named arguments, or null
     * @return whether one is
     */
    static boolean present(final List<FrostlakeParser.NamedArgumentContext> named) {
        if (named != null) {
            for (final FrostlakeParser.NamedArgumentContext argument : named) {
                if (argument.argumentRow() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Refuse the first named argument whose value is a parenthesized list.
     *
     * @param functionName the table function, upper-cased
     * @param positional how many positional arguments come before the named ones
     * @param named the named arguments, or null
     * @param evaluator the evaluator that types and folds each value in the list
     */
    static void refuse(final String functionName, final int positional,
                       final List<FrostlakeParser.NamedArgumentContext> named, final ExpressionEvaluator evaluator) {
        if (named == null) {
            return;
        }
        for (int i = 0; i < named.size(); i++) {
            final FrostlakeParser.NamedArgumentContext argument = named.get(i);
            if (argument.argumentRow() == null) {
                continue;
            }
            if ("GENERATOR".equals(functionName)) {
                throw new RuntimeException(SqlCompilationError.of("argument " + (positional + i + 1)
                    + " to function GENERATOR needs to be constant, found '" + planText(argument.argumentRow(), evaluator)
                    + "'"));
            }
            if ("TO_QUERY".equals(functionName)) {
                throw new RuntimeException(SqlCompilationError.of("argument 0 to function -1 needs to be constant, found '"
                    + argument.identifier().getText() + "'"));
            }
            if ("RESULT_SCAN".equals(functionName)) {
                final Token opening = argument.argumentRow().getStart();
                throw new RuntimeException(SqlCompilationError.at(opening.getLine(), opening.getCharPositionInLine(),
                    "Invalid result query ID, found '('"));
            }
            throw new RuntimeException(SqlCompilationError.of("invalid type [" + typeText(argument.argumentRow(),
                evaluator) + "] for parameter '" + argument.identifier().getText().toUpperCase(Locale.ROOT) + "'"));
        }
    }

    /** The ROW's type as a refusal lists it: each value's type in order. */
    private static String typeText(final FrostlakeParser.ArgumentRowContext row, final ExpressionEvaluator evaluator) {
        final StringBuilder types = new StringBuilder("ROW(");
        for (int i = 0; i < row.expression().size(); i++) {
            if (i > 0) {
                types.append(", ");
            }
            types.append(evaluator.argumentTypeText(element(row, i)));
        }
        return types.append(')').toString();
    }

    /** The ROW as the plan prints it: each constant folded to its value, anything else as written. */
    private static String planText(final FrostlakeParser.ArgumentRowContext row, final ExpressionEvaluator evaluator) {
        final StringBuilder text = new StringBuilder("ROW(");
        for (int i = 0; i < row.expression().size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            final Expression element = element(row, i);
            text.append(isFoldable(element) ? valueText(evaluator.evaluate(element, null))
                : evaluator.plannedText(element));
        }
        return text.append(')').toString();
    }

    private static Expression element(final FrostlakeParser.ArgumentRowContext row, final int index) {
        return ExpressionEvaluator.parse(ParseTreeText.getOriginalText(row.expression(index)));
    }

    /** A literal, or signs and arithmetic or concatenation over literals: what the plan folds to a value. */
    private static boolean isFoldable(final Expression expression) {
        if (expression instanceof LiteralExpression) {
            return true;
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperator operator = ((UnaryOperationExpression) expression).getOperator();
            return (operator == UnaryOperator.NEGATE || operator == UnaryOperator.PLUS)
                && isFoldable(((UnaryOperationExpression) expression).getOperand());
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression operation = (BinaryOperationExpression) expression;
            final BinaryOperator operator = operation.getOperator();
            return (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE
                || operator == BinaryOperator.CONCAT)
                && isFoldable(operation.getLeft()) && isFoldable(operation.getRight());
        }
        return false;
    }

    /** A folded value as the plan prints it. */
    private static String valueText(final Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue() ? "TRUE" : "FALSE";
        }
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(((Number) value).doubleValue()).stripTrailingZeros().toPlainString();
        }
        if (value instanceof Number) {
            final BigDecimal number = new BigDecimal(value.toString());
            return number.signum() == 0 ? "0" : number.stripTrailingZeros().toPlainString();
        }
        return "'" + value.toString().replace("'", "''") + "'";
    }
}
