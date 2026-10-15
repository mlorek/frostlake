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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.HigherOrderFunctionNames;
import dev.frostlake.functions.SnowflakeBuiltinNames;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.BangOperator;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The functions a SQL UDF body calls, judged at CREATE as a real account judges them, once the body's other
 * names have resolved (live-verified):
 * <ul>
 *   <li>a call reaching back to the routine being created — itself, or through the SQL UDFs it calls — is a
 *       cycle, {@code Detected a cycle in SQL UDF: F}, whatever else the body holds;</li>
 *   <li>a call written with OVER must name a window function or an aggregate:
 *       {@code Invalid function type [ABS] for window function.};</li>
 *   <li>every call whose name nothing declares is named in ONE sentence, an inner call before the call
 *       holding it: {@code Unknown functions NOSUCHB, NOSUCHA.}</li>
 * </ul>
 * A name the account has built in counts as declared even where this engine does not implement it, so a
 * routine calling one is created, as it is on the account.
 */
final class SqlUdfBodyCalls {

    private SqlUdfBodyCalls() {
    }

    /**
     * Refuse a cycle, a misused OVER or an unknown function in a SQL UDF body.
     *
     * @param body        the parsed body — a query or an expression
     * @param bodyText    the text the body was parsed from
     * @param routineName the canonical name of the routine being created
     * @param database    the database the routine is created in, or null
     * @param schema      the schema the routine is created in, or null
     */
    static void reject(final QueryExecutor queryExecutor, final ParseTree body, final String bodyText,
                       final String routineName, final String database, final String schema) {
        final List<FrostlakeParser.FunctionCallExprContext> calls = new ArrayList<FrostlakeParser.FunctionCallExprContext>();
        collect(body, calls);
        rejectCycle(queryExecutor, body, routineName, database, schema);
        rejectNonWindowCallsWithOver(queryExecutor.getFunctionRegistry(), calls);
        final ExpressionEvaluator scan = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
            queryExecutor.getCatalog(), queryExecutor);
        final List<FunctionCallExpression> unknown = new ArrayList<FunctionCallExpression>();
        final List<ParserRuleContext> written = new ArrayList<ParserRuleContext>();
        collectNamed(body, written);
        for (final ParserRuleContext one : written) {
            if (!(one instanceof FrostlakeParser.FunctionCallExprContext)) {
                // A leading '!' and a class instance's method are calls of what no account has.
                try {
                    final Expression unnamed = ExpressionEvaluator.parse(
                        bodyText.substring(one.getStart().getStartIndex(), one.getStop().getStopIndex() + 1));
                    if (unnamed instanceof FunctionCallExpression) {
                        unknown.add((FunctionCallExpression) unnamed);
                    }
                } catch (final RuntimeException notACallOnItsOwn) {
                    // judged where it is evaluated
                }
                continue;
            }
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) one;
            final List<String> parts = nameParts(call);
            if (parts.size() == 1 && SnowflakeBuiltinNames.contains(parts.get(0))) {
                continue;
            }
            final Expression parsed;
            try {
                parsed = ExpressionEvaluator.parse(text(call, bodyText));
            } catch (final RuntimeException notACallOnItsOwn) {
                continue;
            }
            if (!(parsed instanceof FunctionCallExpression)) {
                continue;
            }
            if (namedByLiteral(call)) {
                // IDENTIFIER('name')(…) names its function while the body compiles, as a written name does.
                final FunctionCallExpression named = new FunctionCallExpression(String.join(".", parts),
                    ((FunctionCallExpression) parsed).getArguments());
                if (!scan.resolvesToAFunctionName(named) || !qualifiedCallResolves(queryExecutor, parts, database)
                        || !reachedFromTheSession(queryExecutor, parts)) {
                    unknown.add(named);
                }
            } else if (!scan.resolvesToAFunctionName(parsed) || !qualifiedCallResolves(queryExecutor, parts, database)
                    || !reachedFromTheSession(queryExecutor, parts)) {
                unknown.add((FunctionCallExpression) parsed);
            }
        }
        if (!unknown.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(scan.unknownFunctionSentence(unknown)));
        }
        // A tag function the body calls is answered as the function is created (see CompiledTagCalls).
        CompiledTagCalls.requireInFunctionBody(body, queryExecutor);
    }

    /**
     * Whether a QUALIFIED call reaches a function where its qualifier points: one the engine registers under the
     * whole name, or a user-defined one of that name in the schema named. An unqualified call answers true — the
     * general resolution judges it.
     */
    private static boolean qualifiedCallResolves(final QueryExecutor queryExecutor, final List<String> parts,
                                                 final String database) {
        if (parts.size() < 2) {
            return true;
        }
        if (queryExecutor.getFunctionRegistry().hasFunction(String.join(".", parts).toUpperCase(Locale.ROOT))) {
            return true;
        }
        try {
            final String databaseName = parts.size() == 3 ? parts.get(0) : database;
            final Schema schema = queryExecutor.getCatalog().getDatabase(databaseName).getSchema(parts.get(parts.size() - 2));
            return !schema.getFunctionOverloads(parts.get(parts.size() - 1)).isEmpty();
        } catch (final RuntimeException noSuchSchema) {
            return false;
        }
    }

    /**
     * Whether an UNQUALIFIED call reaches a function from where the session is: one the engine registers, or a
     * user-defined one in a schema of the session's search path. A function of that name in a schema the path
     * does not name is not reached — the body compiles in the session's schema whatever schema the routine is
     * created in. A qualified call answers true.
     */
    private static boolean reachedFromTheSession(final QueryExecutor queryExecutor, final List<String> parts) {
        if (parts.size() != 1) {
            return true;
        }
        final String name = parts.get(0);
        final String upper = name.toUpperCase(Locale.ROOT);
        final FunctionRegistry registry = queryExecutor.getFunctionRegistry();
        if (registry.hasFunction(upper) || registry.hasAggregateFunction(upper) || registry.hasTableFunction(upper)
                || WindowFunctionNames.handles(upper) || upper.startsWith("SYSTEM$") || "IDENTIFIER".equals(upper)
                || HigherOrderFunctionNames.contains(upper)) {
            return true;
        }
        final List<Schema> path = queryExecutor.searchPathSchemas();
        if (path.isEmpty()) {
            return true;   // no current schema to judge from
        }
        for (final Schema schema : path) {
            if (!schema.getFunctionOverloads(name).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** Whether a call names its function through IDENTIFIER over a string literal. */
    private static boolean namedByLiteral(final FrostlakeParser.FunctionCallExprContext call) {
        return call.functionName() != null && call.functionName().identifierArgument() != null
            && (call.functionName().identifierArgument().STRING_LITERAL() != null
                || call.functionName().identifierArgument().DOLLAR_QUOTED_STRING() != null);
    }

    /** Whether a refusal is live's unknown-function sentence, for a name this engine could not resolve. */
    static boolean isUnknownFunctionRefusal(final RuntimeException refusal) {
        final String message = String.valueOf(refusal.getMessage());
        return message.contains("Unknown function") || message.contains("Unknown user-defined function");
    }

    private static void rejectCycle(final QueryExecutor queryExecutor, final ParseTree body,
                                    final String routineName, final String database, final String schema) {
        if (routineName == null) {
            return;
        }
        Schema home = null;
        if (database != null && schema != null) {
            try {
                home = queryExecutor.getCatalog().getDatabase(database).getSchema(schema);
            } catch (final RuntimeException noHome) {
                home = null;
            }
        }
        if (reaches(queryExecutor, home, body, routineName, new HashSet<String>(), new HashSet<String>())) {
            throw new RuntimeException("Detected a cycle in SQL UDF: " + routineName);
        }
    }

    /**
     * Whether a body reaches the target by name: through a call it makes, through the body of a SQL function it
     * calls, or through a view it reads — a view's query calls functions as a body does, and a view may read
     * another (live-verified). Only names written with one part are followed, as the account follows them.
     */
    private static boolean reaches(final QueryExecutor queryExecutor, final Schema home, final ParseTree body,
                                   final String target, final Set<String> visitedFunctions,
                                   final Set<String> visitedViews) {
        final List<FrostlakeParser.FunctionCallExprContext> calls =
            new ArrayList<FrostlakeParser.FunctionCallExprContext>();
        collectReached(body, calls);
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final List<String> parts = nameParts(call);
            if (parts.size() != 1) {
                continue;
            }
            final String name = parts.get(0);
            if (name.equals(target)) {
                return true;
            }
            if (home == null || !visitedFunctions.add(name)) {
                continue;
            }
            for (final Function called : home.getFunctionOverloads(name)) {
                if (called.getUdfLanguage() != UdfLanguage.SQL || called.getBody() == null) {
                    continue;
                }
                // A body that closes its own frame before a semicolon calls only what its frame holds.
                final ParseTree calledBody =
                    parse(queryExecutor, SqlUdfBodyFrame.executableBody(called.getBody()).trim());
                if (calledBody != null
                        && reaches(queryExecutor, home, calledBody, target, visitedFunctions, visitedViews)) {
                    return true;
                }
            }
        }
        if (home == null) {
            return false;
        }
        final List<String> read = new ArrayList<String>();
        collectRelationNames(body, read);
        for (final String name : read) {
            if (!home.hasView(name) || !visitedViews.add(name)) {
                continue;
            }
            final String definition = home.getView(name).getDefinition();
            final ParseTree query = definition == null ? null : parse(queryExecutor, definition.trim());
            if (query != null && reaches(queryExecutor, home, query, target, visitedFunctions, visitedViews)) {
                return true;
            }
        }
        return false;
    }

    /** The relations a tree reads by a name of one part: the tables and the views of its FROM clauses. */
    private static void collectRelationNames(final ParseTree node, final List<String> names) {
        if (node instanceof FrostlakeParser.TableQualifiedNameContext) {
            final FrostlakeParser.TableQualifiedNameContext name = (FrostlakeParser.TableQualifiedNameContext) node;
            if (name.namePart().isEmpty() && name.nameStartPart() != null) {
                final String[] parts = SqlIdentifiers.canonicalTextParts(name.nameStartPart().getText());
                if (parts.length == 1) {
                    names.add(parts[0]);
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectRelationNames(node.getChild(i), names);
        }
    }

    private static void rejectNonWindowCallsWithOver(final FunctionRegistry registry,
                                                     final List<FrostlakeParser.FunctionCallExprContext> calls) {
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final List<String> parts = nameParts(call);
            if (call.overClause() == null || parts.isEmpty()) {
                continue;
            }
            final String name = parts.get(parts.size() - 1).toUpperCase(Locale.ROOT);
            final boolean engineKnows = registry.hasFunction(name) || registry.hasTableFunction(name);
            if (WindowFunctionNames.handles(name) || registry.getAggregateFunction(name) != null
                    || (parts.size() == 1 && !engineKnows && SnowflakeBuiltinNames.contains(name))) {
                continue;
            }
            throw new RuntimeException(SqlCompilationError.of("Invalid function type [" + spelled(call.functionName())
                + "] for window function."));
        }
    }

    /** Every call under the node, inner calls before the call holding them, except a TABLE(…) source's own. */
    private static void collect(final ParseTree node, final List<FrostlakeParser.FunctionCallExprContext> calls) {
        if (node instanceof FrostlakeParser.MatchRecognizeClauseContext) {
            return;   // the clause judges its own calls where it is planned: MATCH_NUMBER(), LAG(v) with no OVER
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), calls);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && !(((FrostlakeParser.FunctionCallExprContext) node).getParent() instanceof FrostlakeParser.TableSourceContext)) {
            calls.add((FrostlakeParser.FunctionCallExprContext) node);
        }
    }

    /**
     * Every call under the node that names a function, inner calls first: the calls {@link #collect} gathers, a
     * method of a class instance written without OVER, and a leading {@code !} that negates no EXISTS.
     */
    private static void collectNamed(final ParseTree node, final List<ParserRuleContext> calls) {
        if (node instanceof FrostlakeParser.MatchRecognizeClauseContext) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNamed(node.getChild(i), calls);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && !(((FrostlakeParser.FunctionCallExprContext) node).getParent()
                    instanceof FrostlakeParser.TableSourceContext)
                || node instanceof FrostlakeParser.InstanceMethodCallExprContext
                    && ((FrostlakeParser.InstanceMethodCallExprContext) node).overClause() == null
                || node instanceof FrostlakeParser.BangExprContext
                    && !BangOperator.negatesExists((FrostlakeParser.BangExprContext) node)) {
            calls.add((ParserRuleContext) node);
        }
    }

    /**
     * Every call under the node, a TABLE(…) source's own included: a cycle reaches back through a table function as
     * through any other, {@code SELECT COUNT(*) FROM TABLE(f())} over an {@code f} whose query calls the routine
     * being created (live-verified).
     */
    private static void collectReached(final ParseTree node,
                                       final List<FrostlakeParser.FunctionCallExprContext> calls) {
        for (int i = 0; i < node.getChildCount(); i++) {
            collectReached(node.getChild(i), calls);
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            calls.add((FrostlakeParser.FunctionCallExprContext) node);
        }
    }

    /** A body parsed as a query or as an expression, or null when it is neither. */
    private static ParseTree parse(final QueryExecutor queryExecutor, final String body) {
        final FrostlakeParser.SqlScriptContext query = queryExecutor.queryStatementOf(body);
        if (query != null) {
            return query;
        }
        try {
            return AntlrExpressionParser.parseTree(body);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /** The canonical parts of a call's name; none for a call the grammar names another way. */
    private static List<String> nameParts(final FrostlakeParser.FunctionCallExprContext call) {
        final List<String> parts = new ArrayList<String>();
        if (call.functionName() == null) {
            return parts;
        }
        if (namedByLiteral(call)) {
            final FrostlakeParser.IdentifierArgumentContext literal = call.functionName().identifierArgument();
            final String written = literal.STRING_LITERAL() != null
                ? SqlStringLiterals.decode(literal.STRING_LITERAL().getText())
                : literal.DOLLAR_QUOTED_STRING().getText().substring(2,
                    literal.DOLLAR_QUOTED_STRING().getText().length() - 2);
            for (final String part : SqlIdentifiers.canonicalTextParts(written)) {
                parts.add(part);
            }
            return parts;
        }
        for (final FrostlakeParser.IdentifierContext part : call.functionName().identifier()) {
            parts.add(SqlIdentifiers.canonical(part));
        }
        return parts;
    }

    /** A call's name as a refusal echoes it: an unquoted part upper-cased, a quoted one with its quotes. */
    private static String spelled(final FrostlakeParser.FunctionNameContext name) {
        final StringBuilder spelled = new StringBuilder();
        for (final FrostlakeParser.IdentifierContext part : name.identifier()) {
            final String written = part.getText();
            spelled.append(spelled.length() > 0 ? "." : "")
                .append(written.startsWith("\"") ? written : written.toUpperCase(Locale.ROOT));
        }
        return spelled.length() > 0 ? spelled.toString() : name.getText().toUpperCase(Locale.ROOT);
    }

    private static String text(final FrostlakeParser.FunctionCallExprContext call, final String bodyText) {
        return bodyText.substring(call.getStart().getStartIndex(), call.getStop().getStopIndex() + 1);
    }
}
