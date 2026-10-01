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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericLiteralTypes;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An argument that must be constant, as the plan folds it while the statement compiles — which it does only where
 * every leaf of the argument is a literal; over a column nothing folds and the argument is echoed as written
 * (live-verified through LAST_QUERY_ID's index and GETVARIABLE's name):
 *
 * <ul>
 *   <li>a conversion of a text constant, or of a FLOAT one, into an exact NUMBER is carried out, and so is one of a
 *       number or a text into a FLOAT ({@link FoldedConstantExpression}): {@code TO_NUMBER('1')} is the NUMBER(38,0)
 *       constant 1, {@code '1'::INT} and {@code TRY_CAST('1' AS INT)} too, {@code 1.5::FLOAT} the FLOAT 1.5, and a
 *       TRY conversion that fails is its target's NULL. A text constant is a text literal, or {@code ||}, CONCAT,
 *       UPPER or LOWER over them, which fold to the text they make ({@code '1' || 'x'} is {@code '1x'}); TRIM does
 *       not fold, nor does a cast to text. A conversion that fails, one given a format, a conversion of a VARIANT
 *       and a cast of a number to another NUMBER stay as written — {@code 1::INT} is
 *       {@code CAST(1 AS NUMBER(38,0))};</li>
 *   <li>arithmetic meets its operands as the plan converts them: an exact number beside a FLOAT constant is a FLOAT
 *       constant ({@code 1.5::FLOAT + 1} is {@code 1.5 + 1.0}); in a product or a quotient a text beside an exact
 *       number is the number it reads as ({@link PlanTextNumber}: {@code '1.50' * 1} reads NUMBER(18,1),
 *       {@code ' 1 ' * 1} NUMBER(18,5), {@code 'abc' * 1} {@code TO_NUMBER('abc', 18, 5)}), in a sum, a difference
 *       or a remainder a text spelling a number is that number at its own width ({@code '1.50' + 1} is
 *       {@code 1.5 + (CAST(1 AS NUMBER(3,1)))}), a text beside a FLOAT is a FLOAT, and so are two texts
 *       ({@code '1' * '1'} is {@code 1.0 * 1.0}, {@code '1e2' * '1'} {@code 100.0 * 1.0}, {@code '1.5' + '1'}
 *       {@code 1.5 + 1.0});</li>
 *   <li>a plus before a constant is that constant — {@code +TO_NUMBER('1')} the NUMBER(38,0) 1, {@code +'1'} the
 *       FLOAT 1.0, {@code +1.5} the 1.5 — and a minus before a text reads it as a FLOAT, as a plus before one that
 *       reads as no number does: {@code -'1.5'} is {@code NEGATE(1.5)}, {@code +'abc'}
 *       {@code UNARY PLUS(CAST('abc' AS FLOAT))};</li>
 *   <li>a product of exact numbers with a factor of one or of zero is the other factor, or that zero, cast to the
 *       product's type where its own differs: {@code -1 * 1} is {@code CAST(-1 AS NUMBER(2,0))}, {@code 0 * 5}
 *       {@code CAST(0 AS NUMBER(2,0))}, {@code 1 * TO_NUMBER('2')} the NUMBER(38,0) constant 2. The left factor is
 *       asked first, so {@code TO_NUMBER('1') * 1} keeps the literal 1 and casts it. Every other product, sum,
 *       difference, quotient and call stays as written over its folded operands ({@code 2 * 3}, {@code 1 + 0},
 *       {@code ABS(1)}, {@code NEGATE(1)}), and so does a plus before any of them
 *       ({@code UNARY PLUS(CAST(-1 AS NUMBER(2,0)))} for {@code +(-1 * 1)}).</li>
 * </ul>
 */
final class ConstantRootFold {

    /** The conversions into an exact NUMBER a text or FLOAT constant folds through. */
    private static final Set<String> NUMBER_CONVERSIONS = new HashSet<>(Arrays.asList(
        "TO_NUMBER", "TO_DECIMAL", "TO_NUMERIC", "TRY_TO_NUMBER", "TRY_TO_DECIMAL", "TRY_TO_NUMERIC"));

    /** The conversions into a FLOAT a number or text constant folds through. */
    private static final Set<String> FLOAT_CONVERSIONS = new HashSet<>(Arrays.asList("TO_DOUBLE", "TRY_TO_DOUBLE"));

    /** The calls a text constant folds through. */
    private static final Set<String> TEXT_CALLS = new HashSet<>(Arrays.asList("CONCAT", "UPPER", "LOWER"));

    /** The calls that draw a new value or read the session, which no plan folds. */
    private static final Set<String> NEVER_CONSTANT = new HashSet<>(Arrays.asList(
        "RANDOM", "SEQ1", "SEQ2", "SEQ4", "SEQ8", "GETVARIABLE", "UUID_STRING", "RANDSTR"));

    /** The arithmetic that reads a text constant beside a number as a number. */
    private static final Set<BinaryOperator> TEXT_READING_OPERATORS = new HashSet<>(Arrays.asList(
        BinaryOperator.ADD, BinaryOperator.SUBTRACT, BinaryOperator.MULTIPLY, BinaryOperator.DIVIDE,
        BinaryOperator.MODULO));

    private static final int MAX_PRECISION = 38;

    /** What an evaluation that failed answers, where null is the NULL a TRY conversion answers. */
    private static final Object FAILED = new Object();

    private final ExpressionEvaluatorVisitor visitor;

    private ConstantRootFold(final ExpressionEvaluatorVisitor visitor) {
        this.visitor = visitor;
    }

    /**
     * The argument as the plan folds it.
     *
     * @param argument the argument as written
     * @param visitor  types and evaluates the constants
     * @return the folded argument, or null where a leaf is no literal, when nothing folds
     */
    static Expression fold(final Expression argument, final ExpressionEvaluatorVisitor visitor) {
        if (!literalLeaves(argument, visitor.getFunctionRegistry())) {
            return null;
        }
        return new ConstantRootFold(visitor).folded(argument);
    }

    /** Whether every leaf is a literal, under operators, casts and scalar calls that take arguments. */
    private static boolean literalLeaves(final Expression expr, final FunctionRegistry functions) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof UnaryOperationExpression) {
            return literalLeaves(((UnaryOperationExpression) expr).getOperand(), functions);
        }
        if (expr instanceof CastExpression) {
            return literalLeaves(((CastExpression) expr).getExpression(), functions);
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return binary.getEscape() == null && literalLeaves(binary.getLeft(), functions)
                && literalLeaves(binary.getRight(), functions);
        }
        if (!(expr instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getFunctionName() == null || call.getNameExpression() != null || call.isStar() || call.isDistinct()
                || call.getArguments().isEmpty() || call.getWithinGroupOrdered() != null || hasNamedArgument(call)) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (NEVER_CONSTANT.contains(name) || functions == null || functions.getFunction(name) == null
                || functions.hasAggregateFunction(name)) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (!literalLeaves(argument, functions)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNamedArgument(final FunctionCallExpression call) {
        if (call.getArgumentNames() == null) {
            return false;
        }
        for (final String name : call.getArgumentNames()) {
            if (name != null) {
                return true;
            }
        }
        return false;
    }

    private Expression folded(final Expression expr) {
        if (expr instanceof UnaryOperationExpression) {
            return foldedUnary((UnaryOperationExpression) expr);
        }
        if (expr instanceof CastExpression) {
            return foldedCast((CastExpression) expr);
        }
        if (expr instanceof BinaryOperationExpression) {
            return foldedBinary((BinaryOperationExpression) expr);
        }
        if (expr instanceof FunctionCallExpression) {
            return foldedCall((FunctionCallExpression) expr);
        }
        return expr;
    }

    private Expression foldedUnary(final UnaryOperationExpression unary) {
        final UnaryOperator operator = unary.getOperator();
        if (operator == UnaryOperator.NEGATE && negatedNumber(unary) != null) {
            // A minus written before a number is part of it, -(-1) too.
            return unary;
        }
        final boolean sign = operator == UnaryOperator.NEGATE || operator == UnaryOperator.PLUS;
        if (sign && textOf(unary.getOperand()) != null) {
            // A sign before a text reads it as a FLOAT: -'1.5' is NEGATE(1.5), +'1' the FLOAT 1.0 itself, and
            // -'abc' NEGATE(CAST('abc' AS FLOAT)).
            final Expression source = folded(unary.getOperand());
            final Expression read = converted(new CastExpression(source, "FLOAT"), NumericType.FLOAT);
            if (operator == UnaryOperator.PLUS && read != null) {
                return read;
            }
            return new UnaryOperationExpression(operator, read != null ? read : new CastExpression(source, "FLOAT"));
        }
        final Expression operand = folded(unary.getOperand());
        if (operator == UnaryOperator.PLUS) {
            // A plus before a constant is that constant: +TO_NUMBER('1') is the NUMBER(38,0) 1, +(-1) the -1 and
            // +1.5 the 1.5 — a folded one, which a minus before it NEGATEs: -(+1) is NEGATE(1). Before anything
            // else it stays: +(-1 * 1) is UNARY PLUS(CAST(-1 AS NUMBER(2,0))), +NULL UNARY PLUS(...).
            final Expression constant = plusFolded(operand);
            if (constant != null) {
                return constant;
            }
        }
        return operand == unary.getOperand() ? unary : new UnaryOperationExpression(operator, operand);
    }

    /**
     * The constant a plus before this folded operand stands for, typed as the plus is — two whole digits at the
     * least, {@code +1.5} NUMBER(3,1) where 1.5 is NUMBER(2,1) — or null where the plus is kept.
     */
    private Expression plusFolded(final Expression operand) {
        final boolean folded = operand instanceof FoldedConstantExpression;
        final Object value = folded ? ((FoldedConstantExpression) operand).getValue() : exactValue(operand);
        if (!folded && value == null) {
            return null;
        }
        final DataType type = visitor.inferStaticType(new UnaryOperationExpression(UnaryOperator.PLUS, operand));
        if (!(type instanceof NumericType)) {
            return folded ? operand : null;
        }
        return new FoldedConstantExpression(value, type);
    }

    private Expression foldedCast(final CastExpression cast) {
        final Expression source = folded(cast.getExpression());
        final DataType target = visitor.inferStaticType(cast);
        if (target instanceof NumericType && foldsInto((NumericType) target, cast.getExpression(), source)) {
            final Expression converted = converted(cast, target);
            if (converted != null) {
                return converted;
            }
        }
        return source == cast.getExpression() ? cast : new CastExpression(source, cast.getTargetType(),
            cast.isTryMode(), cast.getDeclaredTarget(), cast.getFieldsModifier());
    }

    private Expression foldedCall(final FunctionCallExpression call) {
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        if (TEXT_CALLS.contains(name)) {
            final String made = textOf(call);
            if (made != null) {
                return new LiteralExpression(made, LiteralType.STRING);
            }
        }
        final List<Expression> args = call.getArguments();
        final List<Expression> folded = new ArrayList<>();
        boolean changed = false;
        for (final Expression argument : args) {
            final Expression one = folded(argument);
            changed = changed || one != argument;
            folded.add(one);
        }
        final boolean number = NUMBER_CONVERSIONS.contains(name) && widthOnly(args);
        final boolean approximate = FLOAT_CONVERSIONS.contains(name) && args.size() == 1;
        if (number || approximate) {
            final DataType target = visitor.inferStaticType(call);
            if (target instanceof NumericType && NumericType.isApproximate(target) == approximate
                    && foldsInto((NumericType) target, args.get(0), folded.get(0))) {
                final Expression converted = converted(call, target);
                if (converted != null) {
                    return converted;
                }
            }
        }
        return changed ? call.withArguments(folded) : call;
    }

    /**
     * Whether a conversion into {@code target} folds over this source: into an exact NUMBER a text or a FLOAT
     * constant, into a FLOAT a text or any number constant.
     */
    private boolean foldsInto(final NumericType target, final Expression written, final Expression source) {
        if (textOf(written) != null || floatConstant(source)) {
            return true;
        }
        return NumericType.isApproximate(target) && exactValue(source) != null;
    }

    /** Whether a conversion's arguments after its value are a precision and a scale, not a format. */
    private static boolean widthOnly(final List<Expression> args) {
        if (args.isEmpty() || args.size() > 3) {
            return false;
        }
        for (int i = 1; i < args.size(); i++) {
            if (!(args.get(i) instanceof LiteralExpression)
                    || ((LiteralExpression) args.get(i)).getType() != LiteralType.INTEGER) {
                return false;
            }
        }
        return true;
    }

    private Expression foldedBinary(final BinaryOperationExpression binary) {
        final BinaryOperator operator = binary.getOperator();
        if (operator == BinaryOperator.CONCAT) {
            final String made = textOf(binary);
            if (made != null) {
                return new LiteralExpression(made, LiteralType.STRING);
            }
        }
        Expression left = folded(binary.getLeft());
        Expression right = folded(binary.getRight());
        if (TEXT_READING_OPERATORS.contains(operator)) {
            final String leftText = textOf(binary.getLeft());
            final String rightText = textOf(binary.getRight());
            if (leftText != null && rightText != null) {
                if (operator != BinaryOperator.MODULO) {
                    left = textAsFloat(left);
                    right = textAsFloat(right);
                }
            } else if (leftText != null && isNumber(right)) {
                left = textOperand(operator, leftText, left, right);
            } else if (rightText != null && isNumber(left)) {
                right = textOperand(operator, rightText, right, left);
            }
        }
        if (operator == BinaryOperator.ADD || operator == BinaryOperator.SUBTRACT
                || operator == BinaryOperator.MULTIPLY) {
            if (floatConstant(left) && exactValue(right) != null) {
                right = floatConstant(exactValue(right));
            } else if (floatConstant(right) && exactValue(left) != null) {
                left = floatConstant(exactValue(left));
            }
        }
        if (operator == BinaryOperator.MULTIPLY) {
            final Expression product = foldedProduct(left, right);
            if (product != null) {
                return product;
            }
        }
        return left == binary.getLeft() && right == binary.getRight() ? binary
            : new BinaryOperationExpression(left, operator, right);
    }

    /**
     * A product of exact numbers with a factor of one or zero, as the plan folds it — the other factor, or the zero,
     * cast to the product's type where its own differs — or null for any other product.
     */
    private Expression foldedProduct(final Expression left, final Expression right) {
        if (isUntypedNull(left) || isUntypedNull(right)) {
            return null;
        }
        final DataType product = visitor.inferStaticType(new BinaryOperationExpression(left, BinaryOperator.MULTIPLY,
            right));
        if (!isExactNumber(product) || !isExactNumber(visitor.inferStaticType(left))
                || !isExactNumber(visitor.inferStaticType(right))) {
            return null;
        }
        final NumericType type = (NumericType) product;
        final BigDecimal leftValue = exactValue(left);
        final BigDecimal rightValue = exactValue(right);
        if (leftValue != null && leftValue.compareTo(BigDecimal.ONE) == 0) {
            return castIfNeeded(right, type);
        }
        if (rightValue != null && rightValue.compareTo(BigDecimal.ONE) == 0) {
            return castIfNeeded(left, type);
        }
        if (leftValue != null && leftValue.signum() == 0) {
            return castIfNeeded(left, type);
        }
        if (rightValue != null && rightValue.signum() == 0) {
            return castIfNeeded(right, type);
        }
        return null;
    }

    private Expression castIfNeeded(final Expression factor, final NumericType type) {
        final DataType own = visitor.inferStaticType(factor);
        if (isExactNumber(own) && ((NumericType) own).getPrecision() == type.getPrecision()
                && ((NumericType) own).getScale() == type.getScale()) {
            return factor;
        }
        return new CastExpression(factor, "NUMBER(" + type.getPrecision() + "," + type.getScale() + ")");
    }

    /**
     * A text constant beside a number, as the arithmetic reads it: beside a FLOAT the FLOAT it spells, in a product
     * or a quotient the factor it reads as ({@link #textFactor}), and in a sum, a difference or a remainder a
     * spelled number at its own width ({@link #textTerm}).
     */
    private Expression textOperand(final BinaryOperator operator, final String text, final Expression operand,
                                   final Expression other) {
        if (isApproximate(other)) {
            return textAsFloat(operand);
        }
        if (operator == BinaryOperator.MULTIPLY || operator == BinaryOperator.DIVIDE) {
            return textFactor(text);
        }
        return textTerm(text, other);
    }

    /**
     * A text term beside an exact number. A text spelling a number is that number at its own width, as a literal
     * of the same value is, raised to the other term's scale where that is the larger — the text's conversion
     * shows no cast of its own: {@code '1.50' + 1} is {@code 1.5 + (CAST(1 AS NUMBER(3,1)))}, {@code '1.50' + 0.25}
     * {@code 1.5 + 0.25}, {@code '1' + 1.5} {@code 1 + 1.5}. Any other text reads as a factor does,
     * {@code ' 1 ' + 1} being {@code 1 + (CAST(1 AS NUMBER(18,5)))} (live-verified).
     */
    private Expression textTerm(final String text, final Expression other) {
        final BigDecimal spelled = PlanTextNumber.spelled(text);
        if (spelled == null) {
            return textFactor(text);
        }
        final NumericType own = NumericLiteralTypes.forDecimal(spelled);
        final DataType otherType = visitor.inferStaticType(other);
        final int otherScale = otherType instanceof NumericType ? Math.max(0, ((NumericType) otherType).getScale()) : 0;
        final int scale = Math.max(own.getScale(), otherScale);
        return new FoldedConstantExpression(spelled, new NumericType("NUMBER",
            Math.min(MAX_PRECISION, own.getPrecision() + scale - own.getScale()), scale));
    }

    /**
     * A text factor beside an exact number, as the number it reads as ({@link PlanTextNumber}): {@code '1.50'} the
     * NUMBER(18,1) 1.5, {@code ' 1 '} the NUMBER(18,5) 1, and a text with no number at that width its conversion,
     * {@code TO_NUMBER('abc', 18, 5)}.
     */
    private static Expression textFactor(final String text) {
        final NumericType type = PlanTextNumber.type(text);
        final BigDecimal read = PlanTextNumber.value(text);
        if (read != null) {
            return new FoldedConstantExpression(read, type);
        }
        return new FunctionCallExpression("TO_NUMBER", new ArrayList<Expression>(Arrays.asList(
            new LiteralExpression(text, LiteralType.STRING),
            new LiteralExpression(Long.valueOf(type.getPrecision()), LiteralType.INTEGER),
            new LiteralExpression(Long.valueOf(type.getScale()), LiteralType.INTEGER))));
    }

    /** A text operand as the FLOAT it reads as, or the operand as it is where it reads as none. */
    private static Expression textAsFloat(final Expression operand) {
        if (!(operand instanceof LiteralExpression) || ((LiteralExpression) operand).getType() != LiteralType.STRING) {
            return operand;
        }
        final String trimmed = String.valueOf(((LiteralExpression) operand).getValue()).trim();
        if (trimmed.isEmpty()) {
            return operand;
        }
        try {
            return floatConstant(new BigDecimal(trimmed));
        } catch (final NumberFormatException notANumber) {
            return operand;
        }
    }

    /** A conversion carried out: its value as a constant of its target, or null where it fails. */
    private Expression converted(final Expression conversion, final DataType target) {
        final Object value = evaluated(conversion);
        if (value == FAILED) {
            return null;
        }
        if (value == null) {
            return new FoldedConstantExpression(null, target);
        }
        if (!(value instanceof Number)) {
            return null;
        }
        if (NumericType.isApproximate(target)) {
            return new FoldedConstantExpression(Double.valueOf(((Number) value).doubleValue()), target);
        }
        final BigDecimal exact = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
        return new FoldedConstantExpression(exact, target);
    }

    private Object evaluated(final Expression expr) {
        try {
            return expr.accept(visitor);
        } catch (final RuntimeException refused) {
            return FAILED;
        }
    }

    /**
     * The text a text constant makes — a text literal, or {@code ||}, CONCAT, UPPER or LOWER over text constants —
     * or null where the expression is no text constant.
     */
    private String textOf(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expr;
            return literal.getType() == LiteralType.STRING && !(literal instanceof FoldedConstantExpression)
                ? String.valueOf(literal.getValue()) : null;
        }
        if (!isTextOperation(expr)) {
            return null;
        }
        final Object made = evaluated(expr);
        return made instanceof String ? (String) made : null;
    }

    private boolean isTextOperation(final Expression expr) {
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return binary.getOperator() == BinaryOperator.CONCAT && textOf(binary.getLeft()) != null
                && textOf(binary.getRight()) != null;
        }
        if (!(expr instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) expr;
        if (call.getFunctionName() == null || call.getNameExpression() != null || call.getArguments().isEmpty()
                || !TEXT_CALLS.contains(call.getFunctionName().toUpperCase(Locale.ROOT))) {
            return false;
        }
        for (final Expression argument : call.getArguments()) {
            if (textOf(argument) == null) {
                return false;
            }
        }
        return true;
    }

    private boolean isNumber(final Expression expr) {
        return visitor.inferStaticType(expr) instanceof NumericType;
    }

    private boolean isApproximate(final Expression expr) {
        return NumericType.isApproximate(visitor.inferStaticType(expr));
    }

    /** A number written as a literal, signed or not, or a folded exact constant; null for anything else. */
    private static BigDecimal exactValue(final Expression expr) {
        if (expr instanceof FoldedConstantExpression) {
            return ((FoldedConstantExpression) expr).exactValue();
        }
        if (expr instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) expr;
            if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
                return null;
            }
            final Object value = literal.getValue();
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }
            return value instanceof Long || value instanceof Integer ? BigDecimal.valueOf(((Number) value).longValue())
                : null;
        }
        return expr instanceof UnaryOperationExpression ? negatedNumber((UnaryOperationExpression) expr) : null;
    }

    /**
     * A minus written before a number, or before such a minus, as the signed number it is; null for any other
     * operand — a plus under it among them, which the plan folds first: -(+1) is NEGATE(1).
     */
    private static BigDecimal negatedNumber(final UnaryOperationExpression unary) {
        if (unary.getOperator() != UnaryOperator.NEGATE) {
            return null;
        }
        final Expression operand = unary.getOperand();
        final BigDecimal value = operand instanceof UnaryOperationExpression
            ? negatedNumber((UnaryOperationExpression) operand)
            : operand instanceof FoldedConstantExpression ? null : exactValue(operand);
        return value == null ? null : value.negate();
    }

    private static boolean floatConstant(final Expression expr) {
        return expr instanceof FoldedConstantExpression && ((FoldedConstantExpression) expr).isApproximate()
            && ((FoldedConstantExpression) expr).getValue() != null;
    }

    private static Expression floatConstant(final BigDecimal value) {
        return new FoldedConstantExpression(Double.valueOf(value.doubleValue()), NumericType.FLOAT);
    }

    private static boolean isUntypedNull(final Expression expr) {
        return expr instanceof LiteralExpression && !(expr instanceof FoldedConstantExpression)
            && ((LiteralExpression) expr).getType() == LiteralType.NULL;
    }

    private static boolean isExactNumber(final DataType type) {
        return type instanceof NumericType && !NumericType.isApproximate(type);
    }
}
