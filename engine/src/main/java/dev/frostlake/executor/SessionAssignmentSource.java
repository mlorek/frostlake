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

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantValue;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * What a session variable's {@code SET} accepts as its source. The account folds the source to a constant while
 * it compiles the statement and refuses one it cannot fold: {@code Unsupported feature 'assignment from
 * non-constant source expression'.} A source that fails as it folds is such a source — {@code 1/0}, {@code
 * 'x'::NUMBER}, {@code 'abc'::BINARY}, {@code IFF(FALSE, 1, 1/0)}, {@code (SELECT 1/0) + 1} — while a branch the
 * fold does not take never fails it ({@code IFF(TRUE, 1, 1/0)} sets 1). A source that is one scalar subquery and
 * nothing more runs as a query of its own instead, so its failure is the query's ({@code SET x = (SELECT 1/0)}
 * answers Division by zero). A compilation error stays itself. Whatever the source, the variable cannot hold a
 * VARIANT, OBJECT or ARRAY value: a source typed one of those is refused when its value is not NULL, whatever the
 * value reads as ({@code GET(PARSE_JSON('{"a":1}'), 'a')}, {@code PARSE_JSON('[1]')[0]}, {@code ARRAY_SLICE(…)}),
 * and so is a value that is one, a JSON null included, while a NULL of those types is taken ({@code NULL::VARIANT},
 * {@code (SELECT NULL::OBJECT)}, {@code IFF(TRUE, NULL::VARIANT, NULL::VARIANT)}) (all live-verified).
 */
final class SessionAssignmentSource {

    /** The account's refusal, full stop included. */
    static final String NON_CONSTANT = "Unsupported feature 'assignment from non-constant source expression'.";

    /**
     * The functions the account's folder does not take: a source calling one anywhere outside the queries it holds
     * is refused as non-constant once it compiles, whatever its value (live-verified). The line is each function's
     * own: UPPER, ABS, LEFT, TO_DATE, DATEADD and LAST_DAY fold, REPLACE, ROUND, REVERSE, DATEDIFF and the *_FROM_PARTS
     * family do not. The semi-structured constructors are among them, so a value read out of one is refused too.
     */
    private static final Set<String> UNFOLDED = new HashSet<>(Arrays.asList(
        "TIME_FROM_PARTS", "TIMEFROMPARTS", "DATE_FROM_PARTS", "DATEFROMPARTS", "TIMESTAMP_FROM_PARTS",
        "TIMESTAMPFROMPARTS", "TIMESTAMP_NTZ_FROM_PARTS", "TIMESTAMPNTZFROMPARTS", "TIMESTAMP_LTZ_FROM_PARTS",
        "TIMESTAMPLTZFROMPARTS", "TIMESTAMP_TZ_FROM_PARTS", "TIMESTAMPTZFROMPARTS", "DATEDIFF", "TIMEDIFF",
        "TIMESTAMPDIFF", "MONTHNAME", "DAYNAME", "REPEAT", "HASH", "RANDOM", "UUID_STRING", "SQUARE", "BITAND",
        "ROUND", "DECODE", "STRTOK", "REGEXP_REPLACE", "SHA2", "SHA2_HEX", "MD5", "MD5_HEX", "BASE64_ENCODE",
        "LENGTH", "LEN", "CHR", "CHAR", "LPAD", "RPAD", "REPLACE", "SQRT", "POWER", "POW", "HEX_ENCODE", "CONTAINS",
        "ASCII", "UNIFORM", "NORMAL", "REVERSE", "TRANSLATE", "EDITDISTANCE", "SOUNDEX", "CHARINDEX",
        "ARRAY_CONSTRUCT", "OBJECT_CONSTRUCT", "PARSE_JSON", "SYSTEM$TYPEOF"));

    private SessionAssignmentSource() {
    }

    /**
     * Whether the source is one scalar subquery, parenthesised or not, and nothing more.
     *
     * @param source the source as parsed
     * @return true for {@code (SELECT …)}
     */
    static boolean isWholeSubquery(final FrostlakeParser.BooleanExprContext source) {
        FrostlakeParser.BooleanExprContext value = source;
        while (value instanceof FrostlakeParser.ValueExprContext) {
            final FrostlakeParser.ExpressionContext expression = ((FrostlakeParser.ValueExprContext) value).expression();
            if (expression instanceof FrostlakeParser.ScalarSubqueryExprContext) {
                return true;
            }
            if (!(expression instanceof FrostlakeParser.ParenExprContext)) {
                return false;
            }
            value = ((FrostlakeParser.ParenExprContext) expression).booleanExpr();
        }
        return false;
    }

    /**
     * The refusal a source's failure to fold becomes: a compilation error stays itself, and so does the failure of
     * a source that calls a SYSTEM$ function, which runs while the statement compiles and fails in its own words
     * however deep it sits ({@code UPPER(SYSTEM$USER_TASK_CANCEL_ONGOING_EXECUTIONS('NO_SUCH_TASK'))} names the
     * missing task); a value the evaluation could not compute — a conversion, an arithmetic fault — is the
     * non-constant refusal. A fault of the engine's own, an unsupported construct or a runtime that is not there
     * stays itself.
     *
     * @param failure what computing the source raised
     * @param source  the source as parsed
     * @return the refusal to raise
     */
    static RuntimeException foldFailure(final RuntimeException failure, final ParseTree source) {
        final String message = failure.getMessage();
        if (!isEvaluationFailure(failure) || SqlCompilationError.isCompilationError(message)
                || message.startsWith("invalid identifier '")
                || callsASystemFunction(source)) {
            return failure;
        }
        return new RuntimeException(NON_CONSTANT, failure);
    }

    /** Whether a failure is the evaluation's own — a value it could not compute — rather than the engine's. */
    private static boolean isEvaluationFailure(final RuntimeException failure) {
        final String message = failure.getMessage();
        return !(failure instanceof NullPointerException || failure instanceof ClassCastException
                || failure instanceof IndexOutOfBoundsException || failure instanceof IllegalStateException
                || failure instanceof UnsupportedOperationException)
            && message != null && !message.startsWith("Unsupported") && !message.contains(" is not available: add ");
    }

    /**
     * Whether a source calls a function the account's folder does not take, outside the queries it holds: see
     * {@link #UNFOLDED}.
     *
     * @param node the source as parsed
     * @return true where the source is no constant
     */
    static boolean callsUnfoldedFunction(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        final FrostlakeParser.FunctionNameContext name = calledName(node);
        if (name != null && UNFOLDED.contains(name.getText().toUpperCase(Locale.ROOT))) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsUnfoldedFunction(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the source reaches a function the folder does not take through a user's SQL function it calls: such a
     * function's body - and the body of one that body calls in turn - is folded with the source, so a body that
     * calls one is refused as the source would be (live-verified for a SQL function over CHR, while one whose body is
     * '1' folds). A built-in of the name answers first, a body that is a query is not folded, and of a name's
     * overloads only those taking the call's count of arguments are read.
     *
     * @param node     the source
     * @param executor the executor that finds the functions
     * @return whether such a function is reached
     */
    static boolean reachesUnfoldedThroughSqlFunction(final ParseTree node, final QueryExecutor executor) {
        return reachesUnfolded(node, executor, new HashSet<Function>());
    }

    private static boolean reachesUnfolded(final ParseTree node, final QueryExecutor executor,
                                           final Set<Function> read) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        final FrostlakeParser.FunctionNameContext name = calledName(node);
        if (name != null && !isBuiltIn(executor, name)) {
            final int arity = argumentCount(node);
            for (final Function function : executor.functionOverloads(name)) {
                if (function.getUdfLanguage() != UdfLanguage.SQL || function.isTableFunction()
                        || arity >= 0 && function.getParameters().size() != arity || !read.add(function)) {
                    continue;
                }
                final ParseTree body = bodyTree(function);
                if (body != null && (callsUnfoldedFunction(body) || reachesUnfolded(body, executor, read))) {
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (reachesUnfolded(node.getChild(i), executor, read)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call's name is a built-in's, which answers ahead of a user's function of the name. */
    private static boolean isBuiltIn(final QueryExecutor executor, final FrostlakeParser.FunctionNameContext name) {
        final String upper = name.getText().toUpperCase(Locale.ROOT);
        return executor.getFunctionRegistry().getFunction(upper) != null
            || executor.getFunctionRegistry().hasAggregateFunction(upper);
    }

    /** How many positional arguments a plain call passes, or -1 for a call of any other shape. */
    private static int argumentCount(final ParseTree node) {
        if (!(node instanceof FrostlakeParser.FunctionCallExprContext)) {
            return -1;
        }
        final FrostlakeParser.FunctionArgListContext arguments =
            ((FrostlakeParser.FunctionCallExprContext) node).functionArgList();
        return arguments == null ? 0 : arguments.functionArg().size();
    }

    /** A SQL function's body as an expression's tree, or null for a body that is a query or no expression. */
    private static ParseTree bodyTree(final Function function) {
        final String body = SqlUdfBodyFrame.executableBody(function.getBody());
        if (body == null) {
            return null;
        }
        try {
            return AntlrExpressionParser.parseTree(body.trim());
        } catch (final RuntimeException noExpression) {
            return null;
        }
    }

    /** The name of a call, or null where the node is no call by a name. */
    private static FrostlakeParser.FunctionNameContext calledName(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) node).functionName();
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).functionName();
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).functionName();
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return ((FrostlakeParser.FunctionCallStarExprContext) node).functionName();
        }
        return null;
    }

    /** Whether a SYSTEM$ function is called anywhere in the source. */
    private static boolean callsASystemFunction(final ParseTree node) {
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).getSymbol().getText().toUpperCase(Locale.ROOT).startsWith("SYSTEM$");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (callsASystemFunction(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The value, when a session variable can hold it (see the class comment).
     *
     * @param value         the source's value
     * @param source        the source as parsed
     * @param queryExecutor the executor whose functions and catalog type the source
     * @return the value
     */
    static Object assignable(final Object value, final FrostlakeParser.BooleanExprContext source,
                             final QueryExecutor queryExecutor) {
        if (value instanceof VariantValue
                || value != null && isSemiStructured(staticTypeOf(DeclarationTypes.sqlExpression(source), queryExecutor))) {
            throw new RuntimeException(NON_CONSTANT);
        }
        return value;
    }

    /** The source's static type, or null when it cannot be typed. */
    private static DataType staticTypeOf(final Expression sql, final QueryExecutor queryExecutor) {
        if (sql == null) {
            return null;
        }
        try {
            return DeclarationTypes.staticType(sql, Collections.<String, DataType>emptyMap(), queryExecutor);
        } catch (final RuntimeException untyped) {
            return null;
        }
    }

    private static boolean isSemiStructured(final DataType type) {
        return type instanceof VariantType || type instanceof ObjectType || type instanceof ArrayType
            || type instanceof StructuredObjectType || type instanceof StructuredArrayType;
    }
}
