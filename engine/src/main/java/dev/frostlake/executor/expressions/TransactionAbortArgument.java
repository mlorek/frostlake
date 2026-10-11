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

import dev.frostlake.executor.TransactionAbort;

import java.util.Locale;
import java.util.Set;

/**
 * How SYSTEM$ABORT_TRANSACTION judges its argument as written (live-verified). A BOOLEAN, a date, a time, a
 * timestamp, a BINARY, an ARRAY and an OBJECT are refused for their type while the statement compiles; so is what
 * is no constant — a column, a subquery, a context function such as CURRENT_TRANSACTION(), a NULL cast to a type —
 * with {@code argument 1 to function SYSTEM$ABORT_TRANSACTION needs to be constant, found '…'}. Of the constants
 * only some name an id: a literal, a signed one, a session variable, a block's variable, a text cast or converted
 * to a number, a number or a text cast to FLOAT, and what a text function or {@code ||} builds. Any other —
 * arithmetic, a number cast to NUMBER or to text, IFF, a VARIANT — answers {@code Bad transaction id}.
 */
final class TransactionAbortArgument {

    private static final Set<String> REFUSED_TYPES = Set.of("BOOLEAN", "DATE", "TIME", "BINARY", "ARRAY", "OBJECT");

    private static final Set<String> NUMBER_CONVERSIONS = Set.of("TO_NUMBER", "TO_DECIMAL", "TO_NUMERIC",
        "TRY_TO_NUMBER", "TRY_TO_DECIMAL", "TRY_TO_NUMERIC");

    private static final Set<String> FLOAT_CONVERSIONS = Set.of("TO_DOUBLE", "TRY_TO_DOUBLE");

    private static final Set<String> TEXT_CONVERSIONS = Set.of("TO_VARCHAR", "TO_CHAR");

    private static final Set<String> NUMBER_TARGETS = Set.of("NUMBER", "DECIMAL", "DEC", "NUMERIC", "INT", "INTEGER",
        "BIGINT", "SMALLINT", "TINYINT", "BYTEINT");

    private static final Set<String> FLOAT_TARGETS = Set.of("FLOAT", "FLOAT4", "FLOAT8", "DOUBLE", "REAL");

    private TransactionAbortArgument() {
    }

    /**
     * Whether an argument of a type is refused for it.
     *
     * @param typeText the argument's type as a type refusal lists it
     * @return whether the call is refused
     */
    static boolean refusesType(final String typeText) {
        for (final String refused : REFUSED_TYPES) {
            if (typeText.startsWith(refused)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the refusal of a NULL cast to a type finds in the plan.
     *
     * @param argument the argument
     * @return the plan's name for the typed NULL, or null when the argument is none
     */
    static String typedNull(final Expression argument) {
        if (!(argument instanceof CastExpression)) {
            return null;
        }
        final CastExpression cast = (CastExpression) argument;
        if (!(cast.getExpression() instanceof LiteralExpression)
                || ((LiteralExpression) cast.getExpression()).getType() != LiteralType.NULL) {
            return null;
        }
        final String target = family(cast.getTargetType());
        if (NUMBER_TARGETS.contains(target)) {
            return "SYSTEM$NULL_TO_FIXED(null)";
        }
        if (FLOAT_TARGETS.contains(target)) {
            return "SYSTEM$NULL_TO_REAL(null)";
        }
        return "VARIANT".equals(target) ? "SYSTEM$NULL_TO_VARIANT(null)" : "SYSTEM$NULL_TO_TEXT(null)";
    }

    /**
     * Reads the id a constant argument names.
     *
     * @param argument the argument as written
     * @param value    its value
     * @param visitor  the evaluator, which types an operand
     * @param inBlock  whether a Snowflake Scripting block is running the call
     * @return the id as a Long, {@link TransactionAbort#BAD}, or null for NULL
     */
    static Object read(final Expression argument, final Object value, final ExpressionEvaluatorVisitor visitor,
                       final boolean inBlock) {
        if (value == null) {
            return null;
        }
        if (argument instanceof LiteralExpression || argument instanceof SessionVarExpression
                || argument instanceof BindVariableExpression) {
            return TransactionAbort.byValue(value);
        }
        if (argument instanceof UnaryOperationExpression) {
            final UnaryOperationExpression signed = (UnaryOperationExpression) argument;
            final boolean sign = signed.getOperator() == UnaryOperator.NEGATE
                || signed.getOperator() == UnaryOperator.PLUS;
            final boolean plain = signed.getOperand() instanceof LiteralExpression
                || signed.getOperand() instanceof SessionVarExpression;
            return sign && plain ? TransactionAbort.byValue(value) : TransactionAbort.BAD;
        }
        if (argument instanceof CastExpression) {
            final CastExpression cast = (CastExpression) argument;
            return converted(family(cast.getTargetType()), cast.getExpression(), value, visitor, inBlock);
        }
        if (argument instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) argument;
            final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
            if (NUMBER_CONVERSIONS.contains(name) && !call.getArguments().isEmpty()) {
                return converted("NUMBER", call.getArguments().get(0), value, visitor, false);
            }
            if (FLOAT_CONVERSIONS.contains(name) && !call.getArguments().isEmpty()) {
                return converted("FLOAT", call.getArguments().get(0), value, visitor, false);
            }
            return value instanceof String && !TEXT_CONVERSIONS.contains(name)
                ? TransactionAbort.text((String) value) : TransactionAbort.BAD;
        }
        if (argument instanceof BinaryOperationExpression
                && ((BinaryOperationExpression) argument).getOperator() == BinaryOperator.CONCAT
                && value instanceof String) {
            return TransactionAbort.text((String) value);
        }
        return TransactionAbort.BAD;
    }

    /**
     * A conversion's id: a text made a number, or anything made a FLOAT; every other conversion names none. A
     * block's NUMBER variable reaches its statement as a number cast to the type it was declared with, and names
     * the id it holds.
     */
    private static Object converted(final String target, final Expression operand, final Object value,
                                    final ExpressionEvaluatorVisitor visitor, final boolean inBlock) {
        if (!(value instanceof Number)) {
            return TransactionAbort.BAD;
        }
        if (FLOAT_TARGETS.contains(target)) {
            return TransactionAbort.roundedNumber((Number) value);
        }
        if (!NUMBER_TARGETS.contains(target)) {
            return TransactionAbort.BAD;
        }
        final boolean boundVariable = inBlock && operand instanceof LiteralExpression;
        return boundVariable || visitor.argumentTypeText(operand).startsWith("VARCHAR")
            ? TransactionAbort.exactNumber((Number) value) : TransactionAbort.BAD;
    }

    /** A type's name without its parameters, upper-cased: NUMBER of NUMBER(10,2), DOUBLE of DOUBLE PRECISION. */
    private static String family(final String targetType) {
        final String upper = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);
        int end = 0;
        while (end < upper.length() && (Character.isLetterOrDigit(upper.charAt(end)) || upper.charAt(end) == '_')) {
            end++;
        }
        return upper.substring(0, end);
    }
}
