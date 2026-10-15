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
import dev.frostlake.executor.expressions.LateConstantRefusal;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.executor.expressions.UnsupportedSubqueryException;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.context.GetDdl;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The relations the SQL functions a statement calls read. The account expands a SQL function's body into the
 * statement that calls it while the statement compiles, so a table the body names that is gone refuses the
 * statement even where no row would call the function — over a table with no row, in a branch never taken, under a
 * condition that keeps nothing, with LIMIT 0 — for a scalar function and a table function alike, through the
 * functions a body calls in turn, and so a view, a table or a function created over such a call is not created.
 * A view the body reads is expanded with it, and one whose own table is gone answers {@code Failure during
 * expansion of view 'V': …}; a call reached through a view is told bare, with no view named. Everything else of
 * the statement is judged first: a column it does not have, and a call given the wrong number of arguments, speak
 * ahead of the function's table (all live-verified). A block compiles no call this way: its expressions are
 * judged as they are reached. Where a name has several overloads the call's argument count leaves open, none is
 * judged here.
 */
public final class CalledRoutineSources {

    private CalledRoutineSources() {
    }

    /**
     * Refuse the first call under a tree whose SQL function reads a relation that is not there.
     *
     * @param tree     a statement, a clause or an expression, as parsed; null reads nothing
     * @param executor the executor compiling it
     */
    public static void require(final ParseTree tree, final QueryExecutor executor) {
        if (tree != null) {
            walk(tree, executor, new HashSet<Function>());
        }
    }

    /**
     * {@link #require} for a query: the refusal waits until the statement's outermost query has been judged — a
     * call given the wrong number of arguments anywhere in it speaks first — and is raised at once where the query
     * is part of another kind of statement.
     *
     * @param tree     a query, as parsed
     * @param executor the executor compiling it
     */
    public static void requireLast(final ParseTree tree, final QueryExecutor executor) {
        try {
            require(tree, executor);
        } catch (final RuntimeException refused) {
            if (!LateConstantRefusal.record(refused, null)) {
                throw refused;
            }
        }
    }

    private static void walk(final ParseTree node, final QueryExecutor executor, final Set<Function> judged) {
        final FrostlakeParser.FunctionNameContext called = CalledRoutineSecrets.calledBy(node);
        if (called != null && !called.identifier().isEmpty()) {
            final List<String> parts = new ArrayList<String>();
            for (final FrostlakeParser.IdentifierContext part : called.identifier()) {
                parts.add(SqlIdentifiers.canonical(part));
            }
            // Which overload a call reaches is settled with its argument types: where the count alone leaves more
            // than one, none is judged here, and the one that is called answers for itself.
            final int written = positionalArguments(node);
            Function reached = null;
            int candidates = 0;
            for (final Function overload : CalledRoutineSecrets.overloads(executor, parts)) {
                if (takes(overload, written)) {
                    reached = overload;
                    candidates++;
                }
            }
            if (candidates == 1) {
                requireEvaluableBody(node, reached, executor);
                if (judged.add(reached)) {
                    requireSourcesOf(reached, executor, judged);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), executor, judged);
        }
    }

    /**
     * A SQL function whose body is a query is expanded into the statement as the scalar subquery it is, each
     * parameter read from the call's argument: where an argument reads the row, the body is a subquery correlated
     * through that parameter, and a shape the account cannot evaluate correlated is refused as written inside the
     * function. {@code SELECT a + 1 WHERE a > 0} called over a column is "Unsupported subquery type cannot be
     * evaluated inside Function object: SQ_WHERE", and over a constant it answers (live-verified).
     */
    private static void requireEvaluableBody(final ParseTree call, final Function function,
                                             final QueryExecutor executor) {
        if (!(call instanceof FrostlakeParser.FunctionCallExprContext) || function.getUdfLanguage() != UdfLanguage.SQL
                || function.isTableFunction() || function.getBody() == null || typedOnly(call)) {
            return;
        }
        final FrostlakeParser.FunctionArgListContext arguments =
            ((FrostlakeParser.FunctionCallExprContext) call).functionArgList();
        final Set<String> rowRead = new HashSet<String>();
        for (int i = 0; arguments != null && i < arguments.functionArg().size()
                && i < function.getParameters().size(); i++) {
            if (readsAColumn(arguments.functionArg(i))) {
                rowRead.add(function.getParameters().get(i).getName().toUpperCase(Locale.ROOT));
            }
        }
        final String body = SqlUdfBodyFrame.executableBody(function.getBody());
        if (rowRead.isEmpty() || body == null || executor.queryStatementOf(body.trim()) == null) {
            return;
        }
        final FrostlakeParser.SelectStatementContext statement = executor.subqueryStatement(body.trim());
        if (statement != null && new CorrelatedSubqueryRule(executor, rowRead).refusesScalar(statement)) {
            throw UnsupportedSubqueryException.inside(function);
        }
    }

    /**
     * Whether a call stands inside SYSTEM$TYPEOF, which types it without planning it: SYSTEM$TYPEOF(sq_where(n))
     * answers NUMBER(6,2) where the call alone is refused (live-verified).
     */
    private static boolean typedOnly(final ParseTree call) {
        for (ParseTree at = call.getParent(); at != null; at = at.getParent()) {
            if (at instanceof FrostlakeParser.SelectStatementContext) {
                return false;
            }
            if (at instanceof FrostlakeParser.SystemFuncExprContext && "SYSTEM$TYPEOF".equalsIgnoreCase(
                    ((FrostlakeParser.SystemFuncExprContext) at).SYSTEM_FUNC().getText())
                    || at instanceof FrostlakeParser.FunctionCallExprContext && "SYSTEM$TYPEOF".equalsIgnoreCase(
                        ((FrostlakeParser.FunctionCallExprContext) at).functionName().getText())) {
                return true;
            }
        }
        return false;
    }

    /** Whether an argument names a column anywhere in it. */
    private static boolean readsAColumn(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsAColumn(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** How many arguments a call written without argument names gives, or -1 for a call that names them. */
    private static int positionalArguments(final ParseTree call) {
        if (!(call instanceof FrostlakeParser.FunctionCallExprContext)) {
            return -1;
        }
        final FrostlakeParser.FunctionArgListContext arguments =
            ((FrostlakeParser.FunctionCallExprContext) call).functionArgList();
        return arguments == null ? 0 : arguments.functionArg().size();
    }

    /** Whether an overload can be the one a call of that many arguments reaches. */
    private static boolean takes(final Function overload, final int written) {
        if (written < 0) {
            return true;
        }
        int required = 0;
        for (final Parameter parameter : overload.getParameters()) {
            if (!parameter.hasDefault()) {
                required++;
            }
        }
        return written >= required && written <= overload.getParameters().size();
    }

    /**
     * The objects the body's GET_DDL calls name with constant text, looked up where the function lives as the call
     * looks them up while a statement compiles: a missing one refuses the statement wherever the function is called,
     * under SYSTEM$TYPEOF, in a branch never taken and over no row alike (live-verified).
     */
    private static void requireDdlObjectsIn(final ParseTree node, final QueryExecutor executor) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final List<FrostlakeParser.FunctionArgContext> arguments = call.functionArgList() == null
                ? new ArrayList<FrostlakeParser.FunctionArgContext>() : call.functionArgList().functionArg();
            if ("GET_DDL".equalsIgnoreCase(call.functionName().getText()) && arguments.size() >= 2
                    && arguments.size() <= 3) {
                final String type = constantText(arguments.get(0));
                final String name = constantText(arguments.get(1));
                final BuiltInFunction getDdl = executor.getFunctionRegistry().getFunction("GET_DDL");
                if (type != null && name != null && getDdl instanceof GetDdl) {
                    ((GetDdl) getDdl).requireObject(type, name);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            requireDdlObjectsIn(node.getChild(i), executor);
        }
    }

    /** The text a call's argument writes as a string literal, or null for any other argument. */
    private static String constantText(final FrostlakeParser.FunctionArgContext argument) {
        if (argument.booleanExpr() == null) {
            return null;
        }
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(argument.booleanExpr()));
            return parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.STRING
                ? String.valueOf(((LiteralExpression) parsed).getValue()) : null;
        } catch (final RuntimeException notALiteral) {
            return null;
        }
    }

    /** The relations a SQL function's body reads, looked for where the function lives, and those of its calls. */
    private static void requireSourcesOf(final Function function, final QueryExecutor executor,
                                         final Set<Function> judged) {
        if (function.getUdfLanguage() != UdfLanguage.SQL || function.getBody() == null) {
            return;
        }
        final String body = SqlUdfBodyFrame.executableBody(function.getBody());
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        final String text = body.trim();
        executor.inRoutineHome(function.getHome(), function.getOwner(), new RoutineBody() {
            @Override
            public Object run() {
                ParseTree parsed = executor.queryStatementOf(text);
                if (parsed == null) {
                    try {
                        parsed = AntlrExpressionParser.parseTree(text);
                    } catch (final RuntimeException noExpression) {
                        // A block's statements are judged as they run.
                        return null;
                    }
                }
                // The body is the function's own, whatever view the statement reached the call through: what it
                // misses is told as a function's miss is, and a view it reads is expanded under that view's name.
                final boolean outer = ViewExpansion.suspend();
                try {
                    new StatementSourceCheck(executor, parsed).checkQueriesIn(parsed);
                    requireDdlObjectsIn(parsed, executor);
                    executor.expandViewsIn(parsed);
                    walk(parsed, executor, judged);
                } finally {
                    ViewExpansion.end(outer);
                }
                return null;
            }
        });
    }
}
