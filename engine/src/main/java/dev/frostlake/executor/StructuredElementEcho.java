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
import dev.frostlake.executor.expressions.CallEcho;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * How an echo of the expression a statement compiled spells a call the plan holds in another form. A call that
 * appends an element to a structured ARRAY has the element converted to the array's element type as the plan
 * converts it, a text into a NUMBER(p,s) element as {@code TO_NUMBER(text, p, s)} and a number as written
 * (live-verified, for ARRAY_APPEND in a VALUES clause:
 * {@code ARRAY_APPEND(CAST(ARRAY_CONSTRUCT(1) AS ARRAY(NUMBER(10,2))), TO_NUMBER('zz', 10, 2))}). A conversion to
 * VARIANT is the cast it is. A constant FLOAT anywhere in the item - a number converted to FLOAT, its negation, and
 * arithmetic over it and number literals - is folded to the double it computes, written as the double prints:
 * {@code TO_VARIANT(0.1::FLOAT)} is {@code CAST(0.1 AS VARIANT)}, {@code ARRAY_CONSTRUCT(0.1::FLOAT)}
 * {@code ARRAY_CONSTRUCT(0.1)}, {@code 1e3::FLOAT} {@code 1000.0}, {@code 1e30::FLOAT} {@code 1.0E30},
 * {@code 0.1::FLOAT + 1} {@code 1.1} and {@code -2.5::FLOAT} {@code -2.5} (live-verified).
 */
public final class StructuredElementEcho implements CallEcho {

    @Override
    public String spell(final FunctionCallExpression call, final ParsedEchoPrinter printer) {
        final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
        if ((call.getNameParts() == null || call.getNameParts().size() == 1) && "TO_VARIANT".equals(name)
                && call.getArguments().size() == 1) {
            return "CAST(" + printer.printed(call.getArguments().get(0)) + " AS VARIANT)";
        }
        if (!"ARRAY_APPEND".equals(name) || call.getArguments().size() != 2) {
            return null;
        }
        final NumericType element = numberElement(call.getArguments().get(0));
        final Expression value = call.getArguments().get(1);
        if (element == null || !(value instanceof LiteralExpression)
                || ((LiteralExpression) value).getType() != LiteralType.STRING) {
            return null;
        }
        return "ARRAY_APPEND(" + printer.printed(call.getArguments().get(0)) + ", TO_NUMBER("
            + printer.printed(value) + ", " + element.getPrecision() + ", " + element.getScale() + "))";
    }

    @Override
    public String spellConstant(final Expression expression, final ParsedEchoPrinter printer) {
        final Double value = floatValue(expression);
        return value == null || value.isInfinite() || value.isNaN() ? null : Double.toString(value.doubleValue());
    }

    /**
     * The double a constant FLOAT expression computes: a number literal converted to FLOAT, its negation, and the
     * arithmetic of such a value with another or with a number literal; null for any other expression.
     */
    private static Double floatValue(final Expression expression) {
        if (expression instanceof CastExpression) {
            final CastExpression cast = (CastExpression) expression;
            if (cast.isTryMode() || cast.isTypedLiteral() || !NumericType.isApproximate(cast.getTargetDataType())) {
                return null;
            }
            final BigDecimal number = numberLiteral(cast.getExpression());
            return number == null ? null : Double.valueOf(number.doubleValue());
        }
        if (expression instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) expression).getOperator() == UnaryOperator.NEGATE) {
            final Double operand = floatValue(((UnaryOperationExpression) expression).getOperand());
            return operand == null ? null : Double.valueOf(-operand.doubleValue());
        }
        if (!(expression instanceof BinaryOperationExpression)) {
            return null;
        }
        final BinaryOperationExpression operation = (BinaryOperationExpression) expression;
        final Double left = floatValue(operation.getLeft());
        final Double right = floatValue(operation.getRight());
        if (left == null && right == null) {
            return null;
        }
        final Double a = left != null ? left : numberValue(operation.getLeft());
        final Double b = right != null ? right : numberValue(operation.getRight());
        if (a == null || b == null) {
            return null;
        }
        switch (operation.getOperator()) {
            case ADD:
                return Double.valueOf(a.doubleValue() + b.doubleValue());
            case SUBTRACT:
                return Double.valueOf(a.doubleValue() - b.doubleValue());
            case MULTIPLY:
                return Double.valueOf(a.doubleValue() * b.doubleValue());
            case DIVIDE:
                return Double.valueOf(a.doubleValue() / b.doubleValue());
            default:
                return null;
        }
    }

    /** A number literal's double, or null for anything else. */
    private static Double numberValue(final Expression expression) {
        final BigDecimal number = numberLiteral(expression);
        return number == null ? null : Double.valueOf(number.doubleValue());
    }

    /** A number literal's value, or null for anything else. */
    private static BigDecimal numberLiteral(final Expression expression) {
        if (!(expression instanceof LiteralExpression)) {
            return null;
        }
        final LiteralExpression literal = (LiteralExpression) expression;
        if (!(literal.getValue() instanceof Number)
                || literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
            return null;
        }
        return new BigDecimal(String.valueOf(literal.getValue()));
    }

    /** The NUMBER element type of a cast to a structured ARRAY, or null for any other array. */
    private static NumericType numberElement(final Expression array) {
        if (!(array instanceof CastExpression)) {
            return null;
        }
        final DataType target = ((CastExpression) array).getDeclaredTarget();
        return target instanceof ArrayType && ((ArrayType) target).getElementType() instanceof NumericType
            ? (NumericType) ((ArrayType) target).getElementType() : null;
    }
}
