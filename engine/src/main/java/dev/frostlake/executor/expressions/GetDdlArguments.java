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

import dev.frostlake.types.ArrayType;
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.GeographyType;
import dev.frostlake.types.GeometryType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.VectorType;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The shapes GET_DDL's arguments must have while the statement compiles. The account reads the object type and
 * name through an export step that folds them before anything runs, and the third argument through an import step
 * that casts it to a boolean:
 *
 * <pre>
 *   type, name  constant   'PUBLIC'  $v  'PUB' || 'LIC'  UPPER(…)  LOWER(CURRENT_DATABASE())  CURRENT_SCHEMA()
 *                          GETVARIABLE('V')  (SELECT 'PUBLIC')
 *               refused    a column  UPPER(T0.N)  TRIM(' PUBLIC ')  IFF(RANDOM() &gt; 2, 'X', 'PUBLIC')  a number
 *   third       folds      TRUE  1  -1  2.5  1.5::FLOAT  'yes'  'tRuE'  'off'  '1'  NULL
 *               refused    'abc'  ' yes '  'TRUE '  ''  '2'  1 = 1  a column  TO_VARIANT(TRUE)  PARSE_JSON('true')
 *               by type    a DATE, TIME, TIMESTAMP, BINARY, ARRAY or OBJECT
 * </pre>
 */
final class GetDdlArguments {

    /** The calls that fold when every argument folds. */
    private static final Set<String> TEXT_FOLDS = Set.of("UPPER", "LOWER", "COLLATE", "CONCAT", "CONCAT_WS");

    /** The session's context functions, which fold with no arguments. */
    private static final Set<String> CONTEXT_FUNCTIONS = Set.of(
        "CURRENT_DATABASE", "CURRENT_SCHEMA", "CURRENT_ROLE", "CURRENT_USER", "CURRENT_WAREHOUSE",
        "CURRENT_ACCOUNT", "CURRENT_ACCOUNT_NAME", "CURRENT_ORGANIZATION_NAME", "CURRENT_REGION", "CURRENT_SESSION",
        "CURRENT_CLIENT", "CURRENT_VERSION", "CURRENT_SECONDARY_ROLES", "CURRENT_AVAILABLE_ROLES",
        "CURRENT_IP_ADDRESS", "CURRENT_STATEMENT", "CURRENT_TRANSACTION");

    /** The texts a cast to BOOLEAN reads, in any case. */
    private static final Set<String> BOOLEAN_WORDS = Set.of(
        "TRUE", "T", "YES", "Y", "ON", "1", "FALSE", "F", "NO", "N", "OFF", "0");

    private GetDdlArguments() {
    }

    /**
     * Whether the object type or name argument folds to constant text while the statement compiles.
     *
     * @param argument  the argument as written
     * @param evaluator reads a session variable's value
     * @return whether the account takes it as a constant
     */
    static boolean isConstantText(final Expression argument, final ExpressionVisitor<Object> evaluator) {
        if (argument instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) argument).getType();
            return type == LiteralType.STRING || type == LiteralType.NULL;
        }
        if (argument instanceof SessionVarExpression) {
            final Object value = argument.accept(evaluator);
            return value == null || value instanceof String;
        }
        if (argument instanceof SubqueryExpression) {
            return true;
        }
        if (argument instanceof BinaryOperationExpression) {
            final BinaryOperationExpression join = (BinaryOperationExpression) argument;
            return join.getOperator() == BinaryOperator.CONCAT
                && isConstantText(join.getLeft(), evaluator) && isConstantText(join.getRight(), evaluator);
        }
        if (!(argument instanceof FunctionCallExpression)) {
            return false;
        }
        final FunctionCallExpression call = (FunctionCallExpression) argument;
        if (call.isDistinct() || call.isStar() || call.getFunctionName() == null || call.getNameExpression() != null) {
            return false;
        }
        final String name = call.getFunctionName().toUpperCase(Locale.ROOT);
        final List<Expression> args = call.getArguments();
        if (CONTEXT_FUNCTIONS.contains(name)) {
            return args.isEmpty();
        }
        if (!TEXT_FOLDS.contains(name) && !"GETVARIABLE".equals(name)) {
            return false;
        }
        for (final Expression inner : args) {
            if (!isConstantText(inner, evaluator)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the third argument's type is one no cast to BOOLEAN takes, refused as an argument type before the
     * object is looked up.
     *
     * @param type the argument's type, or null when it has none
     * @return whether the type is refused
     */
    static boolean isRefusedFlagType(final DataType type) {
        return type instanceof DateTimeType || type instanceof BinaryType || type instanceof ArrayType
            || type instanceof ObjectType || type instanceof GeographyType || type instanceof GeometryType
            || type instanceof VectorType;
    }

    /**
     * Whether the third argument folds to a boolean while the statement compiles: a boolean or numeric literal, a
     * signed or cast one, a string literal that is a boolean word exactly, NULL, or a session variable holding one.
     *
     * @param argument  the argument as written
     * @param evaluator reads a session variable's value
     * @return whether the account's cast folds it
     */
    static boolean foldsToBoolean(final Expression argument, final ExpressionVisitor<Object> evaluator) {
        if (argument instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) argument;
            return literal.getType() == LiteralType.STRING ? isBooleanWord(String.valueOf(literal.getValue()))
                : literal.getType() != LiteralType.BINARY;
        }
        if (argument instanceof UnaryOperationExpression) {
            final UnaryOperationExpression signed = (UnaryOperationExpression) argument;
            return (signed.getOperator() == UnaryOperator.NEGATE || signed.getOperator() == UnaryOperator.PLUS)
                && isNumericLiteral(signed.getOperand());
        }
        if (argument instanceof CastExpression) {
            final CastExpression cast = (CastExpression) argument;
            return !cast.isTryMode() && (isNumericLiteral(cast.getExpression())
                || cast.getExpression() instanceof LiteralExpression
                    && ((LiteralExpression) cast.getExpression()).getType() == LiteralType.BOOLEAN);
        }
        if (argument instanceof SessionVarExpression) {
            final Object value = argument.accept(evaluator);
            return value == null || value instanceof Boolean || value instanceof Number
                || value instanceof String && isBooleanWord((String) value);
        }
        return false;
    }

    private static boolean isNumericLiteral(final Expression expression) {
        return expression instanceof LiteralExpression
            && (((LiteralExpression) expression).getType() == LiteralType.INTEGER
                || ((LiteralExpression) expression).getType() == LiteralType.DECIMAL);
    }

    private static boolean isBooleanWord(final String text) {
        return BOOLEAN_WORDS.contains(text.toUpperCase(Locale.ROOT));
    }
}
