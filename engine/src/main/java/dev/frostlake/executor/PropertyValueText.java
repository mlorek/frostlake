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
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.JsonArrayExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;

import java.util.List;
import java.util.Locale;

/**
 * An argument value the way an {@code invalid value '…' for property 'X'} refusal echoes it: from the plan, every
 * operator spelled as a call — {@code "||"('@st/', 'f/')}, {@code "+"(1, 1)}, {@code "UNARY PLUS"(2)},
 * {@code AND(TRUE, TRUE)} — an array literal as {@code ARRAY_CONSTRUCT('a')}, a parenthesized list as
 * {@code ROW('a', 'b')}, a text literal with its quotes and NULL, TRUE and FALSE in upper case.
 */
final class PropertyValueText {

    private PropertyValueText() {
    }

    /**
     * The echo of one value.
     *
     * @param expression the value
     * @param evaluator the evaluator whose plan text spells what has no call form here
     * @return the echo
     */
    static String of(final Expression expression, final ExpressionEvaluator evaluator) {
        if (expression instanceof LiteralExpression) {
            return literal((LiteralExpression) expression, evaluator);
        }
        if (expression instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            final String operand = of(unary.getOperand(), evaluator);
            if (unary.getOperator() == UnaryOperator.PLUS) {
                return "\"UNARY PLUS\"(" + operand + ")";
            }
            if (unary.getOperator() == UnaryOperator.NEGATE && isNumber(unary.getOperand())) {
                return "-" + operand;
            }
            return unary.getOperator().name() + "(" + operand + ")";
        }
        if (expression instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            final String symbol = symbol(binary.getOperator());
            if (symbol != null) {
                return symbol + "(" + of(binary.getLeft(), evaluator) + ", " + of(binary.getRight(), evaluator) + ")";
            }
        }
        if (expression instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) expression;
            return call.getFunctionName().toUpperCase(Locale.ROOT) + "(" + list(call.getArguments(), evaluator) + ")";
        }
        if (expression instanceof JsonArrayExpression) {
            return "ARRAY_CONSTRUCT(" + list(((JsonArrayExpression) expression).getElements(), evaluator) + ")";
        }
        return evaluator.plannedText(expression);
    }

    /**
     * The echo of a parenthesized list of values.
     *
     * @param elements the values
     * @param evaluator the evaluator
     * @return {@code ROW(…)}
     */
    static String row(final List<Expression> elements, final ExpressionEvaluator evaluator) {
        return "ROW(" + list(elements, evaluator) + ")";
    }

    private static String list(final List<Expression> elements, final ExpressionEvaluator evaluator) {
        final StringBuilder text = new StringBuilder();
        for (final Expression element : elements) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(of(element, evaluator));
        }
        return text.toString();
    }

    private static String literal(final LiteralExpression literal, final ExpressionEvaluator evaluator) {
        if (literal.getType() == LiteralType.NULL || literal.getValue() == null) {
            return "NULL";
        }
        if (literal.getType() == LiteralType.STRING) {
            return "'" + literal.getValue() + "'";
        }
        if (literal.getType() == LiteralType.BOOLEAN) {
            return literal.getValue().toString().toUpperCase(Locale.ROOT);
        }
        if (literal.getType() == LiteralType.INTEGER || literal.getType() == LiteralType.DECIMAL) {
            return literal.getValue().toString();
        }
        return evaluator.plannedText(literal);
    }

    private static boolean isNumber(final Expression expression) {
        return expression instanceof LiteralExpression
            && (((LiteralExpression) expression).getType() == LiteralType.INTEGER
                || ((LiteralExpression) expression).getType() == LiteralType.DECIMAL);
    }

    /** An operator's call spelling; null for one this echo does not spell. */
    private static String symbol(final BinaryOperator operator) {
        switch (operator) {
            case AND:
                return "AND";
            case OR:
                return "OR";
            case ADD:
                return "\"+\"";
            case SUBTRACT:
                return "\"-\"";
            case MULTIPLY:
                return "\"*\"";
            case DIVIDE:
                return "\"/\"";
            case MODULO:
                return "\"%\"";
            case CONCAT:
                return "\"||\"";
            case EQUAL:
                return "\"=\"";
            case NOT_EQUAL:
                return "\"!=\"";
            case LESS_THAN:
                return "\"<\"";
            case LESS_THAN_OR_EQUAL:
                return "\"<=\"";
            case GREATER_THAN:
                return "\">\"";
            case GREATER_THAN_OR_EQUAL:
                return "\">=\"";
            default:
                return null;
        }
    }
}
