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

package dev.frostlake.executor.expressions;

import dev.frostlake.functions.scalar.context.LastQueryId;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;

/**
 * LAST_QUERY_ID's index as the statement compiles it (live-verified). The index is converted to a
 * NUMBER(18,0) and must then be a constant, which only a narrow family is:
 *
 * <pre>
 *   1  -1  (1)  -(1)  -(-1)  +1  1.0  1E0  10000.0   a whole number literal, signed or parenthesised
 *   '1'  '-1'  '1.5'                                  a string literal that reads as a number
 *   NULL  $n                                          NULL (the call answers NULL), a session variable
 *   TO_NUMBER('1')  '1'::INT  1.5::FLOAT  +'1'        a conversion the plan folds ({@link ConstantRootFold}) to
 *   1 * TO_NUMBER('2')  CONCAT('1', '')               a whole number, a numeric text, or a FLOAT short of 1E18
 * </pre>
 *
 * <p>Everything else is refused as not constant — a minus before a plus among them, {@code -(+1)} being
 * {@code NEGATE(1)}, and a FLOAT that converts to no NUMBER(18,0), {@code CAST(NaN AS NUMBER(18,0))}. An index
 * whose every leaf is a literal is echoed as the plan
 * folds it, converted as the index is — a fraction to the whole number of at least eighteen digits that holds
 * its integer part ({@code CAST(1.5 AS NUMBER(18,0))}, {@code CAST(CAST(1 AS NUMBER(38,2)) AS NUMBER(36,0))}),
 * a FLOAT to NUMBER(18,0), a text through {@code TO_NUMBER(x, 18, 0)} — and a product with a factor of one or
 * zero folded, {@code CAST(-1 AS NUMBER(2,0))} for {@code -1 * 1}. Over a column nothing folds and the index is
 * its own plan text. A constant further than 10,000 either way is refused for that, even over no rows.
 */
final class QueryIndexArgument {

    /** The digits the index is converted to at the least. */
    private static final int INDEX_DIGITS = 18;

    /** The first magnitude a FLOAT index no longer converts to NUMBER(18,0) at. */
    private static final double INDEX_BOUND = 1e18;

    private QueryIndexArgument() {
    }

    /**
     * Whether the index is constant once the plan has folded it.
     *
     * @param index   the argument as written
     * @param visitor folds the index
     * @return whether it is constant
     */
    static boolean folds(final Expression index, final ExpressionEvaluatorVisitor visitor) {
        if (folds(index)) {
            return true;
        }
        final Expression folded = ConstantRootFold.fold(index, visitor);
        if (folded instanceof FoldedConstantExpression) {
            final FoldedConstantExpression constant = (FoldedConstantExpression) folded;
            final DataType type = constant.getDeclaredType();
            if (NumericType.isApproximate(type)) {
                return constant.getValue() == null || convertsToIndex(((Number) constant.getValue()).doubleValue());
            }
            return type instanceof NumericType && ((NumericType) type).getScale() <= 0;
        }
        return folded instanceof LiteralExpression && folded != index && folds(folded);
    }

    /**
     * Whether a FLOAT constant converts to the index's NUMBER(18,0), which a constant index must: NaN, the
     * infinities and anything from 1E18 on either way do not, and are refused as not constant —
     * {@code CAST(NaN AS NUMBER(18,0))}, {@code CAST(1.0E18 AS NUMBER(18,0))} (live-verified).
     */
    private static boolean convertsToIndex(final double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && Math.abs(value) < INDEX_BOUND;
    }

    /**
     * The index as the refusal echoes it.
     *
     * @param index   the argument as written
     * @param visitor folds and prints the index
     * @return the echo
     */
    static String echo(final Expression index, final ExpressionEvaluatorVisitor visitor) {
        final Expression folded = ConstantRootFold.fold(index, visitor);
        if (folded == null || folded instanceof LiteralExpression && !(folded instanceof FoldedConstantExpression)) {
            return echo(folded == null ? index : folded, visitor.strictPlanText(folded == null ? index : folded));
        }
        final String printed = visitor.strictPlanText(folded);
        final DataType type = visitor.inferStaticType(folded);
        if (type instanceof StringType) {
            return "TO_NUMBER(" + printed + ", " + INDEX_DIGITS + ", 0)";
        }
        if (NumericType.isApproximate(type)) {
            return "CAST(" + printed + " AS NUMBER(" + INDEX_DIGITS + ",0))";
        }
        if (type instanceof NumericType && ((NumericType) type).getScale() > 0) {
            final NumericType number = (NumericType) type;
            return "CAST(" + printed + " AS NUMBER("
                + Math.max(INDEX_DIGITS, number.getPrecision() - number.getScale()) + ",0))";
        }
        return printed;
    }

    /**
     * Whether the index folds to a constant while the statement compiles.
     *
     * @param index the argument as written
     * @return whether it is constant
     */
    static boolean folds(final Expression index) {
        if (index instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) index;
            if (literal.getType() == LiteralType.STRING) {
                return readsAsNumber((String) literal.getValue());
            }
            return !isFraction(literal);
        }
        if (index instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) index;
            final Expression operand = unary.getOperand();
            if (unary.getOperator() == UnaryOperator.NEGATE) {
                // A minus is part of the number it is written before, or of a minus: -(-1) is 1. Before a plus it
                // is an operator of its own, -(+1) NEGATE(1), no constant (live-verified).
                return (isNumberLiteral(operand) || isNegation(operand)) && folds(operand);
            }
            return unary.getOperator() == UnaryOperator.PLUS && isNumber(operand) && folds(operand);
        }
        return index instanceof SessionVarExpression || index instanceof BindVariableExpression;
    }

    private static boolean isNumberLiteral(final Expression operand) {
        return operand instanceof LiteralExpression && isNumber(operand);
    }

    private static boolean isNegation(final Expression operand) {
        return operand instanceof UnaryOperationExpression
            && ((UnaryOperationExpression) operand).getOperator() == UnaryOperator.NEGATE;
    }

    /**
     * Refuses a constant index further than 10,000 either way.
     *
     * @param index a constant index, as {@link #folds} accepts it
     * @param evaluator reads the constant's value
     */
    static void requireWithinLimit(final Expression index, final ExpressionVisitor<Object> evaluator) {
        final Object value = index.accept(evaluator);
        if (value instanceof Number || value instanceof String && readsAsNumber((String) value)) {
            LastQueryId.requireWithinLimit(LastQueryId.wholeIndex(value));
        }
    }

    /**
     * The index as the refusal echoes it: the conversion a fractional literal or a non-numeric text keeps,
     * or the plan's own text of anything else.
     *
     * @param index the argument as written
     * @param planText the plan's text of the argument
     * @return the echo
     */
    static String echo(final Expression index, final String planText) {
        final BigDecimal fraction = signedFraction(index);
        if (fraction != null) {
            return "CAST(" + fraction.toPlainString() + " AS NUMBER(18,0))";
        }
        if (index instanceof LiteralExpression && ((LiteralExpression) index).getType() == LiteralType.STRING) {
            return "TO_NUMBER('" + ((String) ((LiteralExpression) index).getValue()).replace("'", "\\'")
                + "', 18, 0)";
        }
        return planText;
    }

    /** A number literal whose value is not whole, signed as written, or null for anything else. */
    private static BigDecimal signedFraction(final Expression index) {
        if (index instanceof LiteralExpression && isFraction((LiteralExpression) index)) {
            return decimalOf((LiteralExpression) index);
        }
        if (index instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) index;
            final BigDecimal inner = signedFraction(unary.getOperand());
            if (inner != null && unary.getOperator() == UnaryOperator.NEGATE) {
                return inner.negate();
            }
            if (inner != null && unary.getOperator() == UnaryOperator.PLUS) {
                return inner;
            }
        }
        return null;
    }

    private static boolean isNumber(final Expression operand) {
        if (operand instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) operand).getType();
            return type == LiteralType.INTEGER || type == LiteralType.DECIMAL;
        }
        return operand instanceof UnaryOperationExpression;
    }

    private static boolean isFraction(final LiteralExpression literal) {
        final BigDecimal value = decimalOf(literal);
        return value != null && value.stripTrailingZeros().scale() > 0;
    }

    private static BigDecimal decimalOf(final LiteralExpression literal) {
        if (literal.getType() != LiteralType.DECIMAL) {
            return null;
        }
        final Object value = literal.getValue();
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Double && !((Double) value).isNaN() && !((Double) value).isInfinite()) {
            return new BigDecimal(value.toString());
        }
        return null;
    }

    private static boolean readsAsNumber(final String text) {
        if (text == null) {
            return false;
        }
        try {
            new BigDecimal(text.trim());
            return true;
        } catch (final NumberFormatException notANumber) {
            return false;
        }
    }
}
