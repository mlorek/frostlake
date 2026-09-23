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
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The expressions a VALUES clause refuses because live's compiler does not fold them to a constant: a division,
 * and the math functions below, anywhere in the item. Live refuses
 * {@code INSERT INTO t VALUES (SQRT(4))} and {@code SELECT $1 FROM VALUES (1 / 3)} as
 * {@code Invalid expression [SQRT(4.0)] in VALUES clause}, while FLOOR, MOD, DIV0, GREATEST, COALESCE, NVL,
 * TO_DOUBLE, RADIANS, DEGREES, ABS, PI, BITOR and {@code + - * %} are folded and taken (live-verified, each).
 *
 * <p>The echo is live's rewritten expression, not the text as written:
 *
 * <pre>
 *   SQRT(4)                     SQRT(4.0)             a float parameter prints its argument as a FLOAT
 *   ROUND(1.5), SIGN(-1)        ROUND(1.5), SIGN(-1)  an exact parameter keeps its spelling
 *   CEIL(1.5::FLOAT)            CEIL(1.5)             a FLOAT cast of a literal is that literal
 *   SQRT(4) + 1                 (SQRT(4.0)) + 1.0     a call inside an operator is parenthesised
 *   -SQRT(4)                    NEGATE(SQRT(4.0))
 *   1::FLOAT / 3                1.0 / 3.0             a FLOAT operation prints both operands as FLOATs
 *   TO_VARCHAR(1 / 3)           CAST(1 / 3 AS VARCHAR(134217728))
 *   SQRT(4)::NUMBER             CAST(SQRT(4.0) AS NUMBER(38,0))
 *   CONCAT('a', 1 / 2)          CONCAT('a', CAST(1 / 2 AS VARCHAR(134217728)))
 *   IFF(TRUE, SQRT(4), 1)       SQRT(4.0)             a constant condition folds to its branch
 *   CASE WHEN TRUE THEN SQRT(4) END   ENSURE_NULLABLE(SQRT(4.0))
 * </pre>
 *
 * <p>A shape outside these echoes as written.
 */
final class ValuesFoldRule {

    /** The functions refused in VALUES whose parameters are FLOATs, so a literal argument prints as one. */
    private static final Set<String> FLOAT_FUNCTIONS = Set.of(
        "SQRT", "EXP", "LN", "LOG", "POWER", "POW", "SIN", "COS", "TAN", "COT", "ASIN", "ACOS", "ATAN",
        "ATAN2", "SINH", "COSH", "TANH", "ASINH", "ACOSH", "ATANH", "SQUARE", "CBRT", "HAVERSINE");

    /** The functions refused in VALUES whose arguments keep their spelling. */
    private static final Set<String> EXACT_FUNCTIONS = Set.of(
        "ROUND", "CEIL", "TRUNC", "TRUNCATE", "SIGN", "FACTORIAL", "BITAND", "BITXOR", "BITNOT",
        "BITSHIFTLEFT", "HASH", "WIDTH_BUCKET");

    private static final String VARCHAR_CAST = " AS VARCHAR(134217728))";

    private final ExpressionEvaluator typer;

    private ValuesFoldRule(final ExpressionEvaluator typer) {
        this.typer = typer;
    }

    /**
     * The refusal of a VALUES item live does not fold, or null when the item is taken.
     *
     * @param item      the item, parsed
     * @param written   the item as written, the echo of a shape the rewrite does not cover
     * @param typer     an evaluator that types the item's operations
     * @return the refusal to throw, or null
     */
    static RuntimeException refusalOf(final Expression item, final String written, final ExpressionEvaluator typer) {
        // A name comes first: SQRT(n) is "invalid identifier 'N'", which the item's own reading reports.
        if (item == null || !holdsUnfolded(item) || namesAColumn(item)) {
            return null;
        }
        final String echo = new ValuesFoldRule(typer).print(item, false);
        return new RuntimeException(SqlCompilationError.of(
            "Invalid expression [" + (echo != null ? echo : written.trim()) + "] in VALUES clause"));
    }

    /** Whether a refused function or a division appears anywhere under {@code node}. */
    private static boolean holdsUnfolded(final Expression node) {
        if (node instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) node;
            if (call.getFunctionName() != null && refused(call.getFunctionName())) {
                return true;
            }
            for (final Expression argument : call.getArguments()) {
                if (holdsUnfolded(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof CastExpression) {
            return holdsUnfolded(((CastExpression) node).getExpression());
        }
        if (node instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) node;
            return binary.getOperator() == BinaryOperator.DIVIDE
                || holdsUnfolded(binary.getLeft()) || holdsUnfolded(binary.getRight());
        }
        if (node instanceof UnaryOperationExpression) {
            return holdsUnfolded(((UnaryOperationExpression) node).getOperand());
        }
        if (node instanceof CaseExpression) {
            for (final WhenClause clause : ((CaseExpression) node).getWhenClauses()) {
                if (holdsUnfolded(clause.getCondition()) || holdsUnfolded(clause.getResult())) {
                    return true;
                }
            }
            final Expression otherwise = ((CaseExpression) node).getElseExpression();
            return otherwise != null && holdsUnfolded(otherwise);
        }
        return false;
    }

    /** Whether a column reference appears anywhere under {@code node}. */
    private static boolean namesAColumn(final Expression node) {
        if (node instanceof ColumnReferenceExpression) {
            return true;
        }
        if (node instanceof FunctionCallExpression) {
            for (final Expression argument : ((FunctionCallExpression) node).getArguments()) {
                if (namesAColumn(argument)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof CastExpression) {
            return namesAColumn(((CastExpression) node).getExpression());
        }
        if (node instanceof BinaryOperationExpression) {
            return namesAColumn(((BinaryOperationExpression) node).getLeft())
                || namesAColumn(((BinaryOperationExpression) node).getRight());
        }
        if (node instanceof UnaryOperationExpression) {
            return namesAColumn(((UnaryOperationExpression) node).getOperand());
        }
        return false;
    }

    private static boolean refused(final String name) {
        final String upper = name.toUpperCase(Locale.ROOT);
        return FLOAT_FUNCTIONS.contains(upper) || EXACT_FUNCTIONS.contains(upper);
    }

    /** The echo of {@code node}, its literals printed as FLOATs when {@code asFloat}; null for an unknown shape. */
    private String print(final Expression node, final boolean asFloat) {
        if (node instanceof LiteralExpression) {
            return literal((LiteralExpression) node, asFloat);
        }
        if (node instanceof UnaryOperationExpression) {
            final UnaryOperationExpression unary = (UnaryOperationExpression) node;
            if (unary.getOperator() != UnaryOperator.NEGATE) {
                return null;
            }
            final String operand = print(unary.getOperand(), asFloat);
            if (operand == null) {
                return null;
            }
            return unary.getOperand() instanceof LiteralExpression ? "-" + operand : "NEGATE(" + operand + ")";
        }
        if (node instanceof CastExpression) {
            return cast((CastExpression) node, asFloat);
        }
        if (node instanceof FunctionCallExpression) {
            return call((FunctionCallExpression) node, asFloat);
        }
        if (node instanceof BinaryOperationExpression) {
            return binary((BinaryOperationExpression) node);
        }
        if (node instanceof CaseExpression) {
            final CaseExpression expression = (CaseExpression) node;
            if (expression.getWhenClauses().size() != 1 || expression.getElseExpression() != null
                    || expression.getWhenClauses().get(0).isOperandMatch()
                    || !isTrue(expression.getWhenClauses().get(0).getCondition())) {
                return null;
            }
            final String result = print(expression.getWhenClauses().get(0).getResult(), asFloat);
            return result == null ? null : "ENSURE_NULLABLE(" + result + ")";
        }
        return null;
    }

    private static String literal(final LiteralExpression literal, final boolean asFloat) {
        final Object value = literal.getValue();
        if (literal.getType() == LiteralType.STRING) {
            if (!asFloat) {
                return "'" + String.valueOf(value).replace("'", "\\'") + "'";
            }
            try {
                return floatText(new BigDecimal(String.valueOf(value).trim()));
            } catch (final NumberFormatException notNumeric) {
                return null;
            }
        }
        if (literal.getType() != LiteralType.INTEGER && literal.getType() != LiteralType.DECIMAL) {
            return null;
        }
        final BigDecimal number = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(String.valueOf(value));
        return asFloat ? floatText(number) : number.toPlainString();
    }

    /** A number as a FLOAT literal prints: a whole number gains {@code .0}, a fraction keeps its digits. */
    private static String floatText(final BigDecimal number) {
        return number.scale() <= 0 ? number.toBigInteger() + ".0" : number.toPlainString();
    }

    private String cast(final CastExpression cast, final boolean asFloat) {
        final String target = String.valueOf(cast.getTargetType()).trim().toUpperCase(Locale.ROOT);
        final Expression operand = cast.getExpression();
        if (isFloatName(target)) {
            // A FLOAT cast of a literal folds to a FLOAT literal.
            return operand instanceof LiteralExpression || operand instanceof UnaryOperationExpression
                ? print(operand, true) : null;
        }
        final String inner = print(operand, asFloat);
        if (inner == null) {
            return null;
        }
        if ("VARCHAR".equals(target) || "STRING".equals(target) || "TEXT".equals(target)) {
            return "CAST(" + inner + VARCHAR_CAST;
        }
        return "NUMBER".equals(target) ? "CAST(" + inner + " AS NUMBER(38,0))" : null;
    }

    private String call(final FunctionCallExpression call, final boolean asFloat) {
        final String name = call.getFunctionName() == null ? "" : call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> arguments = call.getArguments();
        if ("TO_VARCHAR".equals(name) && arguments.size() == 1) {
            final String inner = print(arguments.get(0), false);
            return inner == null ? null : "CAST(" + inner + VARCHAR_CAST;
        }
        if ("IFF".equals(name) && arguments.size() == 3 && arguments.get(0) instanceof LiteralExpression
                && ((LiteralExpression) arguments.get(0)).getValue() instanceof Boolean) {
            return print(isTrue(arguments.get(0)) ? arguments.get(1) : arguments.get(2), asFloat);
        }
        if ("NVL".equals(name) && arguments.size() == 2 && arguments.get(0) instanceof FunctionCallExpression
                && holdsUnfolded(arguments.get(0))) {
            // A call that cannot answer NULL folds NVL away.
            return print(arguments.get(0), asFloat);
        }
        final boolean floatParameters = FLOAT_FUNCTIONS.contains(name);
        final StringBuilder out = new StringBuilder(name).append('(');
        for (int i = 0; i < arguments.size(); i++) {
            final Expression argument = arguments.get(i);
            String printed = print(argument, floatParameters);
            if (printed == null) {
                return null;
            }
            if ("CONCAT".equals(name) && !(argument instanceof LiteralExpression
                    && ((LiteralExpression) argument).getType() == LiteralType.STRING)) {
                // CONCAT reads a value that is not text through a cast to VARCHAR.
                printed = "CAST(" + printed + VARCHAR_CAST;
            }
            out.append(i > 0 ? ", " : "").append(printed);
        }
        return out.append(')').toString();
    }

    private String binary(final BinaryOperationExpression binary) {
        final String operator;
        switch (binary.getOperator()) {
            case ADD:
                operator = " + ";
                break;
            case SUBTRACT:
                operator = " - ";
                break;
            case MULTIPLY:
                operator = " * ";
                break;
            case DIVIDE:
                operator = " / ";
                break;
            default:
                return null;
        }
        final boolean asFloat = isFloat(binary);
        final String left = operand(binary.getLeft(), asFloat);
        final String right = operand(binary.getRight(), asFloat);
        return left == null || right == null ? null : left + operator + right;
    }

    /** An operator's operand: a call or another operation parenthesised. */
    private String operand(final Expression operand, final boolean asFloat) {
        final String printed = print(operand, asFloat);
        if (printed == null) {
            return null;
        }
        return operand instanceof FunctionCallExpression || operand instanceof BinaryOperationExpression
            ? "(" + printed + ")" : printed;
    }

    private boolean isFloat(final Expression expression) {
        final DataType type;
        try {
            type = typer.inferStaticType(expression);
        } catch (final RuntimeException untyped) {
            return false;
        }
        return type instanceof NumericType && NumericType.isApproximate(type);
    }

    private static boolean isFloatName(final String target) {
        return "FLOAT".equals(target) || "DOUBLE".equals(target) || "REAL".equals(target)
            || "FLOAT4".equals(target) || "FLOAT8".equals(target) || "DOUBLE PRECISION".equals(target);
    }

    private static boolean isTrue(final Expression condition) {
        return condition instanceof LiteralExpression && Boolean.TRUE.equals(((LiteralExpression) condition).getValue());
    }
}
