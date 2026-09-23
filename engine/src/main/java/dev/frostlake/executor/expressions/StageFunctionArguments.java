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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.file.GetPresignedUrl;
import dev.frostlake.functions.scalar.file.NamedStage;
import dev.frostlake.functions.scalar.file.StageUrlFunction;
import dev.frostlake.parser.StageArgumentSyntax;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringResultWidths;
import dev.frostlake.types.StringType;
import dev.frostlake.types.WidthlessStringType;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules the stage functions' arguments meet while the statement compiles, judged from the arguments AS WRITTEN —
 * so they hold over a table with no rows — and the call itself. Live-verified:
 *
 * <pre>
 *   stage argument   a string literal or a session variable holding one; a bare &#64;st is the literal '&#64;st'
 *     NULL, $unset                 Argument 1 to function 'F' cannot be null or empty.
 *     TRUE, 1, $number             Argument number 1 for function 'F' needs to be a string literal.
 *     x, '&#64;' || 'st', UPPER('&#64;st'), NULL::VARCHAR, CAST('&#64;st' AS VARCHAR(10))
 *                                  argument 1 to function F needs to be constant, found 'X'
 *   GET_PRESIGNED_URL's path       NULL, ''    Argument 2 to function 'GET_PRESIGNED_URL' cannot be null or empty.
 *                                  1, TRUE     Argument number 2 for function 'GET_PRESIGNED_URL' needs to be a string literal.
 *                                  anything else is read with the row, a NULL answering NULL
 *   GET_PRESIGNED_URL's expiry     a numeric literal, negated or not, or a session variable; 60::INT, +60 and 30 + 30
 *                                  are not constant
 *   GET_PRESIGNED_URL of four      Invalid number of arguments (of five or more: too many arguments … expected 4)
 *   BUILD_SCOPED_FILE_URL's third  the literal TRUE or FALSE, else "BUILD_SCOPED_FILE_URL operation expects valid
 *                                  boolean for privatelink argument."
 * </pre>
 *
 * <p>The stage is judged first — a missing stage is refused ahead of a bad path or expiry — and a call of the wrong
 * size is left to the count's own sentence. The value rules on the stage itself are the function's own
 * ({@link StageUrlFunction#requireStage}).
 */
final class StageFunctionArguments {

    private StageFunctionArguments() {
    }

    /**
     * Whether the call is one of the stage functions, as the registry holds it.
     *
     * @param funcName the call's name, upper-cased
     * @param call     the call
     * @param visitor  the evaluator
     * @return the function, or null when the call is none of them
     */
    static StageUrlFunction stageFunction(final String funcName, final FunctionCallExpression call,
                                          final ExpressionEvaluatorVisitor visitor) {
        if (call.getNameExpression() != null || !StageArgumentSyntax.STAGE_FUNCTIONS.contains(funcName)
                || visitor.getFunctionRegistry() == null) {
            return null;
        }
        final BuiltInFunction function = visitor.getFunctionRegistry().getFunction(funcName);
        return function instanceof StageUrlFunction ? (StageUrlFunction) function : null;
    }

    /**
     * Refuse the first argument the call's function refuses while the statement compiles.
     *
     * @param funcName the call's name, upper-cased
     * @param call     the call
     * @param visitor  the evaluator, which reads a session variable and echoes an argument
     */
    static void judge(final String funcName, final FunctionCallExpression call,
                      final ExpressionEvaluatorVisitor visitor) {
        final StageUrlFunction function = stageFunction(funcName, call, visitor);
        if (function == null) {
            return;
        }
        final List<Expression> args = call.getArguments();
        // GET_PRESIGNED_URL declares a fourth argument no call may pass: exactly four is this bare sentence, and
        // more the count sentence, "expected 4".
        if ("GET_PRESIGNED_URL".equals(funcName) && args.size() == function.getMaxArgCount()) {
            throw new RuntimeException("Invalid number of arguments");
        }
        if (args.size() < function.getMinArgCount() || args.size() > function.getMaxArgCount()) {
            return;
        }
        function.requireStage(stageArgument(funcName, args.get(0), visitor));
        if ("GET_PRESIGNED_URL".equals(funcName)) {
            requireStringPath(funcName, args.get(1));
            if (args.size() == GetPresignedUrl.EXPIRY_ARGUMENTS) {
                GetPresignedUrl.expiration(expiry(funcName, args.get(2), visitor));
            }
        }
        if ("BUILD_SCOPED_FILE_URL".equals(funcName) && args.size() > 2 && !isBooleanLiteral(args.get(2))) {
            throw new RuntimeException(SqlCompilationError.inline(
                "BUILD_SCOPED_FILE_URL operation expects valid boolean for privatelink argument."));
        }
    }

    /**
     * The call evaluated once {@link #judge} has passed it: every argument read with the row, handed over with whether
     * the path was a constant the statement folds.
     *
     * @param function the call's function
     * @param call     the call
     * @param visitor  the evaluator
     * @return the result
     */
    static Object call(final StageUrlFunction function, final FunctionCallExpression call,
                       final ExpressionEvaluatorVisitor visitor) {
        final List<Object> values = new ArrayList<>();
        for (final Expression argument : call.getArguments()) {
            values.add(argument.accept(visitor));
        }
        final boolean pathFolded = call.getArguments().size() < 2
            || ConstantTextArgument.isConstant(call.getArguments().get(1), visitor);
        return function.call(values, pathFolded);
    }

    /**
     * A stage function's declared result: a bare VARCHAR, except GET_ABSOLUTE_PATH's, whose width is its stage's
     * location and its path's declared width together — {@code VARCHAR(94)} for an 89-character location and the
     * path {@code 'f.csv'}, one character for the empty path (live-verified through SYSTEM$TYPEOF). A path of no known
     * width leaves it bare.
     *
     * @param funcName the call's name, upper-cased
     * @param call     the call
     * @param visitor  the evaluator
     * @param types    the inferencer the path's width is read with
     * @return the type, or null when the call is none of the stage functions
     */
    static DataType resultType(final String funcName, final FunctionCallExpression call,
                               final ExpressionEvaluatorVisitor visitor, final TypeInferencer types) {
        final StageUrlFunction function = stageFunction(funcName, call, visitor);
        if (function == null) {
            return null;
        }
        final List<Expression> args = call.getArguments();
        if (!"GET_ABSOLUTE_PATH".equals(funcName) || args.size() != 2) {
            return WidthlessStringType.WIDTHLESS;
        }
        final DataType path = types.infer(args.get(1));
        if (!(path instanceof StringType) || path instanceof WidthlessStringType) {
            return WidthlessStringType.WIDTHLESS;
        }
        final NamedStage stage;
        try {
            stage = function.requireStage(stageArgument(funcName, args.get(0), visitor));
        } catch (final RuntimeException refused) {
            return WidthlessStringType.WIDTHLESS;
        }
        final long width = (long) stage.getLocation().length() + Math.max(1, ((StringType) path).getMaxLength());
        return width > StringResultWidths.UNBOUNDED ? WidthlessStringType.WIDTHLESS
            : new StringType("VARCHAR", (int) width);
    }

    /** The stage argument's value, when it is one the account takes as a constant. */
    private static Object stageArgument(final String funcName, final Expression argument,
                                        final ExpressionEvaluatorVisitor visitor) {
        if (argument instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) argument;
            if (literal.getType() == LiteralType.NULL || literal.getType() == LiteralType.STRING) {
                return literal.getValue();
            }
            throw notAStringLiteral(1, funcName);
        }
        if (argument instanceof SessionVarExpression) {
            final Object value = argument.accept(visitor);
            if (value == null || value instanceof String) {
                return value;
            }
            throw notAStringLiteral(1, funcName);
        }
        throw new RuntimeException(SqlCompilationError.of("argument 1 to function " + funcName
            + " needs to be constant, found '" + echo(argument, visitor) + "'"));
    }

    /** GET_PRESIGNED_URL's path: a literal must be text that is neither NULL nor empty. */
    private static void requireStringPath(final String funcName, final Expression path) {
        if (!(path instanceof LiteralExpression)) {
            return;
        }
        final LiteralExpression literal = (LiteralExpression) path;
        if (literal.getType() == LiteralType.NULL
                || literal.getType() == LiteralType.STRING && String.valueOf(literal.getValue()).isEmpty()) {
            throw new RuntimeException(SqlCompilationError.inline(
                "Argument 2 to function '" + funcName + "' cannot be null or empty."));
        }
        if (literal.getType() != LiteralType.STRING) {
            throw notAStringLiteral(2, funcName);
        }
    }

    /**
     * GET_PRESIGNED_URL's expiry as the constant it must be: a numeric literal, negated any number of times or not,
     * or a session variable. A plus is no sign here: {@code +60} is the expression {@code UNARY PLUS(60)}. Any other
     * literal reaches the range rule as the value no number is.
     */
    private static Object expiry(final String funcName, final Expression argument,
                                 final ExpressionEvaluatorVisitor visitor) {
        if (argument instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) argument).getType();
            return type == LiteralType.INTEGER || type == LiteralType.DECIMAL
                ? ((LiteralExpression) argument).getValue() : null;
        }
        if (argument instanceof SessionVarExpression || isNegatedNumber(argument)) {
            return argument.accept(visitor);
        }
        throw new RuntimeException(SqlCompilationError.of("argument 3 to function " + funcName
            + " needs to be constant, found '" + echo(argument, visitor) + "'"));
    }

    /** A numeric literal under one or more minus signs: {@code -60}, {@code -(-60)}. */
    private static boolean isNegatedNumber(final Expression argument) {
        if (!(argument instanceof UnaryOperationExpression)
                || ((UnaryOperationExpression) argument).getOperator() != UnaryOperator.NEGATE) {
            return false;
        }
        final Expression operand = ((UnaryOperationExpression) argument).getOperand();
        if (operand instanceof LiteralExpression) {
            final LiteralType type = ((LiteralExpression) operand).getType();
            return type == LiteralType.INTEGER || type == LiteralType.DECIMAL;
        }
        return isNegatedNumber(operand);
    }

    /**
     * An argument as the constancy sentence quotes it: a column by its bare name, a cast of a literal as the cast the
     * statement wrote, its target the type the cast resolves to ({@code CAST(null AS VARCHAR(134217728))} for
     * {@code NULL::STRING}, {@code CAST('@st' AS VARCHAR(5))} for a CHAR(5), {@code CAST(null AS NUMBER(38,0))} for
     * {@code NULL::INT}), anything else as the plan prints it.
     */
    private static String echo(final Expression argument, final ExpressionEvaluatorVisitor visitor) {
        if (argument instanceof ColumnReferenceExpression
                && ((ColumnReferenceExpression) argument).getColumnName() != null) {
            return SqlIdentifiers.spellCanonical(((ColumnReferenceExpression) argument).getColumnName());
        }
        if (argument instanceof CastExpression && !((CastExpression) argument).isTryMode()
                && ((CastExpression) argument).getExpression() instanceof LiteralExpression) {
            final LiteralExpression literal = (LiteralExpression) ((CastExpression) argument).getExpression();
            final String target = visitor.argumentTypeText(argument);
            if (target != null) {
                final String text = literal.getType() == LiteralType.NULL ? "null"
                    : literal.getType() == LiteralType.STRING
                        ? "'" + String.valueOf(literal.getValue()).replace("'", "''") + "'"
                        : visitor.strictPlanText(literal);
                return "CAST(" + text + " AS " + target + ")";
            }
        }
        return visitor.strictPlanText(argument);
    }

    private static boolean isBooleanLiteral(final Expression argument) {
        return argument instanceof LiteralExpression
            && ((LiteralExpression) argument).getType() == LiteralType.BOOLEAN;
    }

    private static RuntimeException notAStringLiteral(final int position, final String funcName) {
        return new RuntimeException(SqlCompilationError.of("Argument number " + position + " for function '"
            + funcName + "' needs to be a string literal."));
    }
}
