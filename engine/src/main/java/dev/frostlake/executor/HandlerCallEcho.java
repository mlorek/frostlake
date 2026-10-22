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
import dev.frostlake.executor.expressions.CallEcho;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Volatility;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * A call of a function a handler answers — Java, JavaScript, Python, Scala — as the plan names it (live-verified):
 * {@code EXTENSION_FUNC_V} over its arguments, {@code EXTENSION_FUNC_P} for a function declared IMMUTABLE, a whole
 * number given for a FLOAT parameter written as the FLOAT it becomes, {@code jdbl(1)} reading
 * {@code EXTENSION_FUNC_V(1.0)}, an argument of constants folded, {@code cnt(1 + 1)} reading
 * {@code EXTENSION_FUNC_V(2.0)}, and a NULL as {@code null}. Arithmetic around the call is written as the plan types
 * it: in FLOAT arithmetic a constant is the FLOAT it becomes, folded — {@code cnt(1) - (1 + 1)} reads
 * {@code (EXTENSION_FUNC_V(1.0)) - 2.0} — and an exact call is cast, {@code CAST(EXTENSION_FUNC_V(1) AS FLOAT)}; a
 * whole call added to or taken from a decimal is cast to the decimal's scale,
 * {@code (CAST(EXTENSION_FUNC_V(1) AS NUMBER(38,2))) + 1.25}.
 */
final class HandlerCallEcho implements CallEcho {

    /** The plan's name for a call a handler answers. */
    private static final String PLAN_NAME = "EXTENSION_FUNC_V";

    /** The plan's name for a call of a handler function declared IMMUTABLE. */
    private static final String IMMUTABLE_PLAN_NAME = "EXTENSION_FUNC_P";

    private final ExpressionEvaluatorVisitor scope;
    /** Whether the print in progress has spelled a handler's call. */
    private boolean spelled;

    /**
     * @param scope the visitor that resolves a call to the function it names
     */
    HandlerCallEcho(final ExpressionEvaluatorVisitor scope) {
        this.scope = scope;
    }

    /**
     * The expression as the plan prints it when a handler's function is called anywhere in it, or null.
     *
     * @param node the expression
     * @return its text, or null when it calls no handler
     */
    String echoedIfHandled(final Expression node) {
        spelled = false;
        final String text = ParsedEchoPrinter.print(node, this);
        return spelled ? text : null;
    }

    @Override
    public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        final Function handled = scope.handlerFunctionCalled(call);
        if (handled == null) {
            return null;
        }
        spelled = true;
        final List<Parameter> parameters = handled.getParameters();
        final List<Expression> arguments = call.getArguments();
        final StringBuilder text = new StringBuilder(
            Volatility.IMMUTABLE.name().equals(handled.getVolatility()) ? IMMUTABLE_PLAN_NAME : PLAN_NAME).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            final DataType parameter = i < parameters.size() ? parameters.get(i).getDataType() : null;
            text.append(i > 0 ? ", " : "").append(argument(arguments.get(i), parameter, printer));
        }
        return text.append(')').toString();
    }

    @Override
    public String spellOperand(final BinaryOperationExpression operation, final Expression operand,
                               final ParsedEchoPrinter printer) {
        if (!isArithmetic(operation.getOperator())) {
            return null;
        }
        final DataType type = arithmeticType(operation);
        if (type == null) {
            return null;
        }
        if (NumericType.isApproximate(type)) {
            final Double constant = foldedDouble(operand);
            if (constant != null) {
                return floatText(constant);
            }
            final DataType own = operandType(operand);
            if (own != null && !NumericType.isApproximate(own) && isHandlerCall(operand)) {
                return "(CAST(" + printer.printed(operand) + " AS FLOAT))";
            }
            return null;
        }
        final BinaryOperator operator = operation.getOperator();
        if ((operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT) && isHandlerCall(operand)) {
            final DataType own = operandType(operand);
            final Expression other = operand == operation.getLeft() ? operation.getRight() : operation.getLeft();
            final int scale = decimalScale(other);
            if (scale > 0 && own instanceof NumericType && ((NumericType) own).getScale() == 0) {
                return "(CAST(" + printer.printed(operand) + " AS NUMBER(38," + scale + ")))";
            }
        }
        return null;
    }

    /** An argument as the call receives it: a whole number for a FLOAT parameter as that FLOAT, constants folded. */
    private static String argument(final Expression argument, final DataType parameter,
                                   final ParsedEchoPrinter printer) {
        if (argument instanceof LiteralExpression && parameter != null && NumericType.isApproximate(parameter)) {
            final Object value = ((LiteralExpression) argument).getValue();
            if (value instanceof Number && !(value instanceof Double) && !(value instanceof Float)) {
                final BigDecimal number = new BigDecimal(value.toString());
                if (number.scale() <= 0) {
                    return number.toBigInteger() + ".0";
                }
            }
        }
        if (!(argument instanceof LiteralExpression) && parameter instanceof NumericType) {
            if (NumericType.isApproximate(parameter)) {
                final Double constant = foldedDouble(argument);
                if (constant != null) {
                    return floatText(constant);
                }
            } else {
                final BigInteger whole = foldedWhole(argument);
                if (whole != null) {
                    return whole.toString();
                }
            }
        }
        return printer.printed(argument);
    }

    /** Whether an expression is a call a handler answers. */
    private boolean isHandlerCall(final Expression expr) {
        return expr instanceof FunctionCallExpression
            && scope.handlerFunctionCalled((FunctionCallExpression) expr) != null;
    }

    private static boolean isArithmetic(final BinaryOperator operator) {
        return operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
            || operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE;
    }

    /** The type an arithmetic operation's operands meet in: FLOAT when either is one, or null when one is unknown. */
    private DataType arithmeticType(final BinaryOperationExpression operation) {
        final DataType left = operandType(operation.getLeft());
        final DataType right = operandType(operation.getRight());
        if (left == null || right == null) {
            return null;
        }
        return NumericType.isApproximate(left) || NumericType.isApproximate(right) ? NumericType.FLOAT
            : NumericType.NUMBER;
    }

    /** The numeric type of an operand this echo reads: a handler's call, a number, arithmetic of them; else null. */
    private DataType operandType(final Expression expr) {
        if (expr instanceof FunctionCallExpression) {
            final Function handled = scope.handlerFunctionCalled((FunctionCallExpression) expr);
            return handled != null && handled.getReturnType() instanceof NumericType ? handled.getReturnType() : null;
        }
        if (expr instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) expr).getValue();
            if (!(value instanceof Number)) {
                return null;
            }
            return value instanceof Double || value instanceof Float ? NumericType.FLOAT : NumericType.NUMBER;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            return unary.getOperator() == UnaryOperator.NEGATE || unary.getOperator() == UnaryOperator.PLUS
                ? operandType(unary.getOperand()) : null;
        }
        if (expr instanceof BinaryOperationExpression
                && isArithmetic(((BinaryOperationExpression) expr).getOperator())) {
            return arithmeticType((BinaryOperationExpression) expr);
        }
        return null;
    }

    /** The value of an expression of number constants as a double, or null for any other expression. */
    private static Double foldedDouble(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) expr).getValue();
            return value instanceof Number ? Double.valueOf(((Number) value).doubleValue()) : null;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            final Double operand = foldedDouble(unary.getOperand());
            if (operand == null) {
                return null;
            }
            if (unary.getOperator() == UnaryOperator.NEGATE) {
                return Double.valueOf(-operand.doubleValue());
            }
            return unary.getOperator() == UnaryOperator.PLUS ? operand : null;
        }
        if (!(expr instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression operation = (BinaryOperationExpression) expr;
        final Double left = foldedDouble(operation.getLeft());
        final Double right = left == null ? null : foldedDouble(operation.getRight());
        if (right == null) {
            return null;
        }
        switch (operation.getOperator()) {
            case ADD:
                return Double.valueOf(left.doubleValue() + right.doubleValue());
            case SUBTRACT:
                return Double.valueOf(left.doubleValue() - right.doubleValue());
            case MULTIPLY:
                return Double.valueOf(left.doubleValue() * right.doubleValue());
            case DIVIDE:
                return right.doubleValue() == 0 ? null : Double.valueOf(left.doubleValue() / right.doubleValue());
            default:
                return null;
        }
    }

    /** The value of an expression of whole number constants under +, - and *, or null for any other expression. */
    private static BigInteger foldedWhole(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            final Object value = ((LiteralExpression) expr).getValue();
            if (!(value instanceof Number) || value instanceof Double || value instanceof Float) {
                return null;
            }
            final BigDecimal number = new BigDecimal(value.toString());
            return number.scale() <= 0 ? number.toBigInteger() : null;
        }
        if (expr instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) expr;
            final BigInteger operand = foldedWhole(unary.getOperand());
            if (operand == null) {
                return null;
            }
            if (unary.getOperator() == UnaryOperator.NEGATE) {
                return operand.negate();
            }
            return unary.getOperator() == UnaryOperator.PLUS ? operand : null;
        }
        if (!(expr instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression operation = (BinaryOperationExpression) expr;
        final BigInteger left = foldedWhole(operation.getLeft());
        final BigInteger right = left == null ? null : foldedWhole(operation.getRight());
        if (right == null) {
            return null;
        }
        switch (operation.getOperator()) {
            case ADD:
                return left.add(right);
            case SUBTRACT:
                return left.subtract(right);
            case MULTIPLY:
                return left.multiply(right);
            default:
                return null;
        }
    }

    /** A FLOAT constant as the plan writes it: a whole value with one decimal, {@code 2.0}, else its digits. */
    private static String floatText(final double value) {
        if (!Double.isInfinite(value) && !Double.isNaN(value) && value == Math.rint(value) && Math.abs(value) < 1e15) {
            return new BigDecimal(value).toBigInteger() + ".0";
        }
        return Double.toString(value);
    }

    /** The scale of a decimal literal, negated or not; zero for any other expression. */
    private static int decimalScale(final Expression expr) {
        if (expr instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expr).getOperator() == UnaryOperator.NEGATE) {
            return decimalScale(((UnaryOperationExpression) expr).getOperand());
        }
        if (expr instanceof LiteralExpression && ((LiteralExpression) expr).getValue() instanceof BigDecimal) {
            return Math.max(((BigDecimal) ((LiteralExpression) expr).getValue()).scale(), 0);
        }
        return 0;
    }
}
