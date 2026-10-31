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

import dev.frostlake.executor.expressions.ArrayAccessExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.CaseExpression;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.JsonArrayExpression;
import dev.frostlake.executor.expressions.JsonObjectExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.ObjectAccessExpression;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.WhenClause;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VariantType;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A VALUES item that holds a semi-structured part anywhere is refused while the statement compiles, in a FROM and in
 * an INSERT alike (live-verified): an array or an object constructor, a value cast to VARIANT, OBJECT or ARRAY, an
 * element read off one, a call that answers one - PARSE_JSON, TO_ARRAY, SPLIT, STRTOK_TO_ARRAY - and a call that
 * reads one - ARRAY_SIZE, TYPEOF, TO_JSON, IS_ARRAY, CHECK_JSON - its argument a NULL included. {@code VALUES
 * (ARRAY_SIZE([1]))} is {@code Invalid expression [ARRAY_SIZE(ARRAY_CONSTRUCT(1))] in VALUES clause}, the item echoed
 * as the plan spells it (see {@link ValuesItemEcho}), while ABS, UPPER, CONCAT, IFF, a cast of a text to a number
 * and a scalar subquery are taken. In a row after the first, an item that IS a semi-structured value is refused by
 * its type, before its column is folded: {@code VALUES (1, 2), ([1], 3)} is {@code Invalid data type [ARRAY] in
 * VALUES clause}.
 */
final class ValuesSemiStructuredParts {

    /** The functions that read a semi-structured value whatever they answer, beyond the ARRAY_ and OBJECT_ ones. */
    private static final Set<String> READERS = new HashSet<>(Arrays.asList(
        "TYPEOF", "TO_JSON", "CHECK_JSON", "GET", "GET_PATH", "GET_IGNORE_CASE", "STRIP_NULL_VALUE",
        "IS_ARRAY", "IS_OBJECT", "IS_BOOLEAN", "IS_BINARY", "IS_CHAR", "IS_VARCHAR", "IS_DATE", "IS_DATE_VALUE",
        "IS_DECIMAL", "IS_DOUBLE", "IS_REAL", "IS_INTEGER", "IS_NULL_VALUE", "IS_TIME", "IS_TIMESTAMP_LTZ",
        "IS_TIMESTAMP_NTZ", "IS_TIMESTAMP_TZ", "AS_ARRAY", "AS_BINARY", "AS_BOOLEAN", "AS_CHAR", "AS_VARCHAR",
        "AS_DATE", "AS_DECIMAL", "AS_NUMBER", "AS_DOUBLE", "AS_REAL", "AS_INTEGER", "AS_OBJECT", "AS_TIME",
        "AS_TIMESTAMP_LTZ", "AS_TIMESTAMP_NTZ", "AS_TIMESTAMP_TZ"));

    private ValuesSemiStructuredParts() {
    }

    /**
     * The refusal of a VALUES item holding a semi-structured part, or null for an item that holds none.
     *
     * @param item      the item, parsed; null when it did not parse as an expression
     * @param typer     the evaluator that types the item's parts
     * @param functions the built-in functions
     * @return the refusal, or null
     */
    static RuntimeException refusalOf(final Expression item, final ExpressionEvaluator typer,
                                      final FunctionRegistry functions) {
        if (item == null || !holdsOne(item, typer, functions)) {
            return null;
        }
        return new RuntimeException(SqlCompilationError.of("Invalid expression ["
            + ParsedEchoPrinter.print(item, new ValuesItemEcho(typer)) + "] in VALUES clause"));
    }

    /**
     * The refusal of an item of a row after the first that is a semi-structured value, by its type, or null.
     *
     * @param item  the item, parsed; null when it did not parse as an expression
     * @param typer the evaluator that types it
     * @return the refusal, or null
     */
    static RuntimeException typeRefusalOf(final Expression item, final ExpressionEvaluator typer) {
        final DataType type = typeOf(item, typer);
        if (!isSemiStructured(type)) {
            return null;
        }
        return new RuntimeException(SqlCompilationError.of("Invalid data type ["
            + (type instanceof ArrayType ? "ARRAY" : type instanceof ObjectType ? "OBJECT" : "VARIANT")
            + "] in VALUES clause"));
    }

    /** Whether an expression holds a semi-structured part anywhere. */
    static boolean holdsOne(final Expression node, final ExpressionEvaluator typer, final FunctionRegistry functions) {
        if (node instanceof JsonArrayExpression || node instanceof JsonObjectExpression
                || node instanceof ArrayAccessExpression || node instanceof ObjectAccessExpression) {
            return true;
        }
        if (node instanceof CastExpression) {
            final CastExpression cast = (CastExpression) node;
            if (isNullLiteral(cast.getExpression()) || isVariantRoundTrip(cast)) {
                // A NULL of any type folds, and so does a constant through a VARIANT back to a scalar: a NULL cast to
                // an ARRAY and a text cast to a VARIANT and back to a VARCHAR are taken (live-verified).
                return false;
            }
            return isSemiStructured(cast.getTargetDataType()) || holdsOne(cast.getExpression(), typer, functions);
        }
        if (node instanceof FunctionCallExpression) {
            final FunctionCallExpression call = (FunctionCallExpression) node;
            if (readsOne(call) || answersOne(call, typer, functions)) {
                return true;
            }
            for (final Expression argument : call.getArguments()) {
                if (holdsOne(argument, typer, functions)) {
                    return true;
                }
            }
            return false;
        }
        if (node instanceof BinaryOperationExpression) {
            return holdsOne(((BinaryOperationExpression) node).getLeft(), typer, functions)
                || holdsOne(((BinaryOperationExpression) node).getRight(), typer, functions);
        }
        if (node instanceof UnaryOperationExpression) {
            return holdsOne(((UnaryOperationExpression) node).getOperand(), typer, functions);
        }
        if (node instanceof IsNullExpression) {
            return holdsOne(((IsNullExpression) node).getOperand(), typer, functions);
        }
        if (node instanceof CaseExpression) {
            final CaseExpression conditional = (CaseExpression) node;
            if (conditional.getElseExpression() != null
                    && holdsOne(conditional.getElseExpression(), typer, functions)) {
                return true;
            }
            for (final WhenClause when : conditional.getWhenClauses()) {
                if (holdsOne(when.getCondition(), typer, functions) || holdsOne(when.getResult(), typer, functions)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a call reads a semi-structured value: an ARRAY_ or OBJECT_ function, or one of {@link #READERS}. */
    static boolean readsOne(final FunctionCallExpression call) {
        if (call.getFunctionName() == null || call.getNameParts() != null && call.getNameParts().size() > 1) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        return READERS.contains(name) || name.startsWith("ARRAY_") || name.startsWith("OBJECT_");
    }

    /**
     * Whether a call answers a semi-structured value. A conditional the registry declares a nominal VARIANT answers
     * its branches' type, which the static channel knows: IFF(TRUE, 1, 2) is a NUMBER, and it is taken.
     */
    private static boolean answersOne(final FunctionCallExpression call, final ExpressionEvaluator typer,
                                      final FunctionRegistry functions) {
        if (call.getFunctionName() == null) {
            return false;
        }
        final BuiltInFunction function = functions.getFunction(call.getFunctionName().toUpperCase(Locale.ROOT));
        if (function == null || !isSemiStructured(function.getReturnType())) {
            return false;
        }
        if (!(function.getReturnType() instanceof VariantType)) {
            return true;
        }
        final DataType typed = typeOf(call, typer);
        return typed == null || isSemiStructured(typed);
    }

    private static DataType typeOf(final Expression expression, final ExpressionEvaluator typer) {
        if (expression == null) {
            return null;
        }
        try {
            return typer.inferStaticType(expression);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }

    private static boolean isNullLiteral(final Expression expression) {
        return expression instanceof LiteralExpression && ((LiteralExpression) expression).getValue() == null;
    }

    /** Whether a cast to a scalar type reads a literal cast to VARIANT, which folds back to the literal. */
    private static boolean isVariantRoundTrip(final CastExpression cast) {
        if (isSemiStructured(cast.getTargetDataType()) || !(cast.getExpression() instanceof CastExpression)) {
            return false;
        }
        final CastExpression inner = (CastExpression) cast.getExpression();
        return inner.getTargetDataType() instanceof VariantType && inner.getExpression() instanceof LiteralExpression
            && !isNullLiteral(inner.getExpression());
    }

    static boolean isSemiStructured(final DataType type) {
        return type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType;
    }
}
