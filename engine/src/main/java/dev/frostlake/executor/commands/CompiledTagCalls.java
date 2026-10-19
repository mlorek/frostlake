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
package dev.frostlake.executor.commands;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionAstBuilder;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.PolicyObjectTagCalls;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The tag functions answered while what holds them compiles. SYSTEM$GET_TAG looks its tag and its object up
 * before any statement of a block runs — in a RETURN, a LET, a condition, a DECLARE default, a branch that never
 * runs, a handler's statement and a query one of those expressions reads alike — so a tag, an object or a domain
 * that is missing is the block's refusal, bare and past every handler; the two functions that read a tag of the
 * object a policy protects are refused the same way, a block being no policy's body. A procedure and a function
 * whose body holds such a call are refused as they are created (all live-verified).
 *
 * <p>The call compiles before the block binds anything. A {@code :name} among its arguments is a bind nothing
 * set, refused where it stands — in a procedure's body as a bind no definition may hold. A variable written by
 * its bare name leaves SYSTEM$GET_TAG to the expression that holds it, which reads the variable's value; inside
 * an argument the name has no value yet, and an argument that is NULL for that is no constant. In a function's
 * body a parameter is no constant either, and is named as the plan names it. A call a statement of the block
 * makes — a SELECT of its own — is that statement's, judged when it runs.
 */
final class CompiledTagCalls {

    private static final String GET_TAG = "SYSTEM$GET_TAG";

    private CompiledTagCalls() {
    }

    /**
     * Answer every such call of a block's own expressions, letting the first refusal out.
     *
     * @param block         the block, before any of it runs
     * @param queryExecutor the executor whose catalog the lookups read, or null where there is none
     */
    static void require(final FrostlakeParser.BeginEndBlockContext block, final QueryExecutor queryExecutor) {
        if (queryExecutor != null) {
            walk(block, queryExecutor, TagCallHolder.BLOCK);
        }
    }

    /**
     * {@link #require} for the block a procedure is created with.
     *
     * @param block         the procedure's body
     * @param queryExecutor the executor whose catalog the lookups read, or null where there is none
     */
    static void requireInProcedureBody(final FrostlakeParser.BeginEndBlockContext block,
                                       final QueryExecutor queryExecutor) {
        if (queryExecutor != null) {
            walk(block, queryExecutor, TagCallHolder.PROCEDURE);
        }
    }

    /**
     * Answer every such call of a function's body — an expression, a row or a query.
     *
     * @param body          the body as parsed
     * @param queryExecutor the executor whose catalog the lookups read, or null where there is none
     */
    static void requireInFunctionBody(final ParseTree body, final QueryExecutor queryExecutor) {
        if (queryExecutor != null && body != null) {
            walk(body, queryExecutor, TagCallHolder.FUNCTION);
        }
    }

    private static void walk(final ParseTree node, final QueryExecutor queryExecutor, final TagCallHolder holder) {
        if (holder != TagCallHolder.FUNCTION && (node instanceof FrostlakeParser.CursorSourceContext
                || node instanceof FrostlakeParser.CursorDeclarationContext
                || node instanceof FrostlakeParser.ResultSetSourceContext
                || node instanceof FrostlakeParser.ResultSetBlockContext
                || node instanceof FrostlakeParser.ResultSetStatementContext
                || node instanceof FrostlakeParser.AsyncJobContext
                || node instanceof FrostlakeParser.CallStatementContext
                || node instanceof FrostlakeParser.ExecuteImmediateStatementContext
                || node instanceof FrostlakeParser.StatementContext
                    && !ownsItsExpressions((FrostlakeParser.StatementContext) node))) {
            return;
        }
        if (node instanceof FrostlakeParser.SystemFuncExprContext) {
            answer((FrostlakeParser.SystemFuncExprContext) node, queryExecutor, holder);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), queryExecutor, holder);
        }
    }

    /** Whether a statement of the block is one whose expressions the block itself compiles. */
    private static boolean ownsItsExpressions(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.ProceduralStatementContext own = statement.proceduralStatement();
        return own != null && (own.beginEndBlock() != null || own.letStatement() != null
            || own.assignmentStatement() != null || own.ifStatement() != null || own.caseStatement() != null
            || own.loopStatement() != null || own.whileStatement() != null || own.repeatStatement() != null
            || own.forStatement() != null || own.returnStatement() != null);
    }

    private static void answer(final FrostlakeParser.SystemFuncExprContext call, final QueryExecutor queryExecutor,
                               final TagCallHolder holder) {
        final String name = call.SYSTEM_FUNC().getText();
        final boolean lookup = GET_TAG.equalsIgnoreCase(name);
        if (!lookup && !PolicyObjectTagCalls.names(name)) {
            return;
        }
        // A call of the wrong count is its expression's own refusal, placed where the expression is.
        final List<FrostlakeParser.BooleanExprContext> written = call.booleanExprList() == null
            ? new ArrayList<FrostlakeParser.BooleanExprContext>() : call.booleanExprList().booleanExpr();
        if (lookup ? written.size() < 3 : written.size() != 1) {
            return;
        }
        if (holder != TagCallHolder.FUNCTION) {
            final Token bind = firstBind(call.booleanExprList());
            if (bind != null) {
                throw new RuntimeException(SqlCompilationError.at(bind.getLine(), bind.getCharPositionInLine(),
                    holder == TagCallHolder.PROCEDURE ? "Bind variables not allowed in view and UDF definitions."
                        : "Bind variable null not set."));
            }
        }
        final Expression parsed;
        try {
            parsed = new ExpressionAstBuilder().visit(call);
        } catch (final RuntimeException unreadable) {
            return;
        }
        if (!(parsed instanceof FunctionCallExpression)) {
            return;
        }
        final List<Expression> arguments = ((FunctionCallExpression) parsed).getArguments();
        if (!lookup) {
            // No policy's body: the call is refused whatever names its tag.
            PolicyObjectTagCalls.judge(name, arguments.get(0));
            return;
        }
        boolean constant = true;
        for (int i = 0; i < 3; i++) {
            if (!readsAName(written.get(i))) {
                continue;
            }
            constant = false;
            final boolean bare = arguments.get(i) instanceof ColumnReferenceExpression;
            if (holder == TagCallHolder.FUNCTION && bare) {
                // A parameter is no constant, and the plan names it by what it is.
                throw new RuntimeException(SqlCompilationError.of("Invalid value [CORRELATION(null)] for function '"
                    + GET_TAG + "', parameter " + i + ": constant arguments expected"));
            }
            if (holder != TagCallHolder.FUNCTION && !bare && isNullWithoutItsNames(written.get(i), arguments.get(i),
                    queryExecutor)) {
                throw new RuntimeException(SqlCompilationError.of("argument " + i + " to function " + GET_TAG
                    + " needs to be constant, found 'null'"));
            }
        }
        if (constant) {
            evaluator(queryExecutor).evaluate(parsed, new Row(new ArrayList<>()));
        }
    }

    private static ExpressionEvaluator evaluator(final QueryExecutor queryExecutor) {
        return new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(), queryExecutor.getCatalog(),
            queryExecutor);
    }

    /**
     * Whether an argument that reads variables answers NULL while none of them has a value: compiled before the
     * block binds, {@code UPPER(n)} is NULL whatever {@code n} will hold. An argument that cannot be answered so
     * is left to the expression that holds it.
     */
    private static boolean isNullWithoutItsNames(final ParseTree written, final Expression argument,
                                                 final QueryExecutor queryExecutor) {
        final Map<String, Object> unbound = new HashMap<>();
        collectNames(written, unbound);
        final ExpressionEvaluator evaluator = evaluator(queryExecutor);
        evaluator.setResultContext(unbound);
        try {
            return evaluator.evaluate(argument, new Row(new ArrayList<>())) == null;
        } catch (final RuntimeException unanswered) {
            return false;
        }
    }

    private static void collectNames(final ParseTree node, final Map<String, Object> into) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.put(node.getText().toUpperCase(Locale.ROOT), null);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNames(node.getChild(i), into);
        }
    }

    /** The first {@code :name} under the node, in the order written, or null. */
    private static Token firstBind(final ParseTree node) {
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            return ((FrostlakeParser.BindVarExprContext) node).getStart();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstBind(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether an argument reads a name — a variable, a parameter — whose value compiling does not have. */
    private static boolean readsAName(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext
                || node instanceof FrostlakeParser.BindVarExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsAName(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
