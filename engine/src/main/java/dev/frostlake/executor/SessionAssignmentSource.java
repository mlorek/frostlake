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

import dev.frostlake.executor.expressions.Expression;
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

import java.util.Collections;
import java.util.Locale;

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
