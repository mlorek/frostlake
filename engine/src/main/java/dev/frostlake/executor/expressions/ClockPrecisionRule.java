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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DecimalText;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The precision a clock function is called with: CURRENT_TIMESTAMP(n), LOCALTIMESTAMP(n), CURRENT_TIME(n) and
 * LOCALTIME(n) take a whole-number constant between 0 and 9, judged while the statement compiles. A number outside
 * the range is refused at the call:
 *
 * <pre>
 *   SELECT CURRENT_TIMESTAMP(10)      error line 1 at position 7
 *                                     Invalid timestamp scale: 10. Must be between 0 and 9.
 *   SELECT CURRENT_TIME(10)           Invalid time scale: 10. Must be between 0 and 9.
 * </pre>
 *
 * <p>A value of a type that never converts to a number is refused at the call too, under the name the call was
 * written with — {@code Invalid argument types for function 'LOCALTIMESTAMP': (BOOLEAN)}. Anything else that is
 * no whole-number constant is echoed as the plan holds it once it converts it to a whole number, placed nowhere
 * and under the name of the function the call stands for:
 *
 * <pre>
 *   NULL             Invalid value [SYSTEM$NULL_TO_FIXED(null)] for function 'CURRENT_TIMESTAMP', parameter 1:
 *                    must be an integer constant
 *   '3'              TO_NUMBER('3', 18, 0)
 *   3.5              CAST(3.5 AS NUMBER(2,0))
 *   3::NUMBER(10,2)  CAST(CAST(3 AS NUMBER(10,2)) AS NUMBER(8,0))
 *   PI()             CAST(PI() AS NUMBER(18,0))
 *   1 + 2            1 + 2
 * </pre>
 *
 * (live-verified).
 */
final class ClockPrecisionRule {

    private static final Set<String> TIMESTAMPS = new HashSet<>(Arrays.asList("CURRENT_TIMESTAMP", "LOCALTIMESTAMP"));
    private static final Set<String> TIMES = new HashSet<>(Arrays.asList("CURRENT_TIME", "LOCALTIME"));

    /** The fewest whole digits the plan converts a number with a fraction to. */
    private static final int LEAST_WHOLE_DIGITS = 2;

    private ClockPrecisionRule() {
    }

    /**
     * Refuse a clock call whose precision is no whole-number constant between 0 and 9.
     *
     * @param funcName the call's name, upper-cased
     * @param call     the call
     * @param context  the evaluator, which places and echoes the refusal
     */
    static void require(final String funcName, final FunctionCallExpression call,
                        final ExpressionEvaluatorVisitor context) {
        final boolean timestamp = TIMESTAMPS.contains(funcName);
        final List<Expression> args = call.getArguments();
        if (!timestamp && !TIMES.contains(funcName) || args.size() != 1) {
            return;
        }
        final Expression precision = args.get(0);
        final BigDecimal whole = wholeConstant(precision);
        if (whole != null) {
            if (whole.signum() < 0 || whole.compareTo(BigDecimal.valueOf(9)) > 0) {
                throw context.refusedAt(call, "Invalid " + (timestamp ? "timestamp" : "time") + " scale: "
                    + whole.toPlainString() + ". Must be between 0 and 9.");
            }
            return;
        }
        final DataType type = isNull(precision) ? null : context.inferStaticType(precision);
        if (type != null && !(type instanceof NumericType) && !(type instanceof StringType)
                && !(type instanceof VariantType)) {
            throw context.refusedAt(call, "Invalid argument types for function '" + funcName + "': ("
                + context.argumentTypeText(precision) + ")");
        }
        throw new RuntimeException(SqlCompilationError.of("Invalid value [" + converted(precision, type, context)
            + "] for function '" + (timestamp ? "CURRENT_TIMESTAMP" : "CURRENT_TIME")
            + "', parameter 1: must be an integer constant"));
    }

    /** The whole number an argument is written as, a minus before it included; null for anything else. */
    private static BigDecimal wholeConstant(final Expression argument) {
        if (argument instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) argument).getOperator() == UnaryOperator.NEGATE) {
            final BigDecimal negated = wholeConstant(((UnaryOperationExpression) argument).getOperand());
            return negated == null ? null : negated.negate();
        }
        final BigDecimal value = number(argument);
        return value == null || value.scale() > 0 ? null : value;
    }

    /** The number a literal is, or null for anything else. */
    private static BigDecimal number(final Expression argument) {
        if (!(argument instanceof LiteralExpression)
                || !(((LiteralExpression) argument).getValue() instanceof Number)) {
            return null;
        }
        return DecimalText.parse(((LiteralExpression) argument).getValue().toString());
    }

    private static boolean isNull(final Expression argument) {
        return argument instanceof LiteralExpression && ((LiteralExpression) argument).getType() == LiteralType.NULL;
    }

    private static boolean isText(final Expression argument) {
        return argument instanceof LiteralExpression && ((LiteralExpression) argument).getType() == LiteralType.STRING;
    }

    /** The argument as the plan holds it once it converts it to a whole number. */
    private static String converted(final Expression argument, final DataType type,
                                    final ExpressionEvaluatorVisitor context) {
        final String held = held(argument, context);
        if (type instanceof StringType || type instanceof VariantType) {
            return "TO_NUMBER(" + held + ", 18, 0)";
        }
        if (type instanceof NumericType && isFloat(type)) {
            return "CAST(" + held + " AS NUMBER(18,0))";
        }
        if (type instanceof NumericType && ((NumericType) type).getScale() > 0) {
            final NumericType number = (NumericType) type;
            return "CAST(" + held + " AS NUMBER("
                + Math.max(number.getPrecision() - number.getScale(), LEAST_WHOLE_DIGITS) + ",0))";
        }
        return held;
    }

    /** The argument as the plan holds it: a constant as it is, a cast spelled out, a NULL by the kind it is. */
    private static String held(final Expression argument, final ExpressionEvaluatorVisitor context) {
        if (isNull(argument)) {
            return "SYSTEM$NULL_TO_FIXED(null)";
        }
        if (isText(argument)) {
            return "'" + String.valueOf(((LiteralExpression) argument).getValue()).replace("'", "''") + "'";
        }
        if (number(argument) != null) {
            return number(argument).toPlainString();
        }
        if (argument instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) argument).getOperator() == UnaryOperator.NEGATE
                && number(((UnaryOperationExpression) argument).getOperand()) != null) {
            return "-" + number(((UnaryOperationExpression) argument).getOperand()).toPlainString();
        }
        if (argument instanceof CastExpression && !((CastExpression) argument).isTryMode()) {
            final CastExpression cast = (CastExpression) argument;
            final DataType target = cast.getDeclaredTarget() != null ? cast.getDeclaredTarget()
                : context.inferStaticType(cast);
            if (target != null) {
                return heldCast(cast.getExpression(), target, context);
            }
        }
        return context.strictPlanText(argument);
    }

    /** A cast as the plan holds it: one that changes nothing is its operand, and a NULL is a NULL of the type. */
    private static String heldCast(final Expression operand, final DataType target,
                                   final ExpressionEvaluatorVisitor context) {
        if (isNull(operand)) {
            return target instanceof StringType ? "SYSTEM$NULL_TO_TEXT(null)"
                : target instanceof NumericType && isFloat(target) ? "SYSTEM$NULL_TO_REAL(null)"
                : target instanceof NumericType ? "SYSTEM$NULL_TO_FIXED(null)"
                : "CAST(" + held(operand, context) + " AS " + SqlTypeNames.canonical(target) + ")";
        }
        if (isText(operand) && target instanceof StringType) {
            return held(operand, context);
        }
        final BigDecimal value = number(operand);
        if (value != null && target instanceof NumericType && !isFloat(target)
                && ((NumericType) target).getScale() == value.scale()
                && ((NumericType) target).getPrecision() == Math.max(value.precision(), value.scale())) {
            return held(operand, context);
        }
        return "CAST(" + held(operand, context) + " AS " + SqlTypeNames.canonical(target) + ")";
    }

    private static boolean isFloat(final DataType type) {
        return NumericType.isApproximate(type);
    }
}
