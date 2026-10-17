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
import dev.frostlake.executor.expressions.VolatileCalls;
import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.functions.scalar.context.GetDdl;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.antlr.v4.runtime.ParserRuleContext;
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
 * judged as they are reached. Where a name has several overloads the call's argument count leaves open, the one the
 * arguments' declared types settle is judged — an exact number and the bare NULL reach a number parameter, a text a
 * text one: {@code f_o(1)}, {@code f_o(a)} over an INT column and {@code f_o(NULL)} reach {@code f_o(p INT)}, where
 * {@code f_o('a')} reaches {@code f_o(p VARCHAR)} (live-verified); any other kind settles nothing here.
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
        require(tree, executor, null);
    }

    /**
     * {@link #require} with the arguments of the statement's own calls typed in its scope, which settles an overload
     * their count leaves open.
     *
     * @param tree     a statement, a clause or an expression, as parsed; null reads nothing
     * @param executor the executor compiling it
     * @param typer    types an argument in the statement's scope; null types none
     */
    public static void require(final ParseTree tree, final QueryExecutor executor,
                               final ExpressionEvaluator typer) {
        if (tree != null) {
            walk(tree, executor, new HashSet<String>(), Collections.<String>emptySet(),
                Collections.<String>emptySet(), Collections.<String, Object>emptyMap(), typer);
        }
    }

    /**
     * {@link #require} for a SQL function's own body as it is created: the body's parameters stand for the values a
     * call passes, so a call inside the body that hands one on reads no row until a call of the function passes it
     * one (live: {@code CREATE FUNCTION w(a NUMBER) … AS 'sq_where(a) * 10'} is created and {@code w(5)} answers).
     *
     * @param body       the body, as parsed
     * @param executor   the executor compiling it
     * @param parameters the function's parameter names, canonical
     */
    public static void requireInBody(final ParseTree body, final QueryExecutor executor,
                                     final Set<String> parameters) {
        if (body != null) {
            walk(body, executor, new HashSet<String>(), parameters, Collections.<String>emptySet(),
                Collections.<String, Object>emptyMap(), null);
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
        requireLast(tree, executor, null);
    }

    /**
     * {@link #requireLast} with the statement's own call arguments typed in its scope (see {@link #require}).
     *
     * @param tree     a query, as parsed
     * @param executor the executor compiling it
     * @param typer    types an argument in the query's scope; null types none
     */
    public static void requireLast(final ParseTree tree, final QueryExecutor executor,
                                   final ExpressionEvaluator typer) {
        try {
            require(tree, executor, typer);
        } catch (final RuntimeException refused) {
            if (!LateConstantRefusal.record(refused, null)) {
                throw refused;
            }
        }
    }

    /**
     * Judges the calls under a tree. Inside a function's body, a name that is one of {@code parameters} reads a row
     * only when it is one of {@code rowParameters} — a parameter the call of that function passed a row's value — and
     * one of {@code constants} holds the constant value the call passed.
     */
    private static void walk(final ParseTree node, final QueryExecutor executor, final Set<String> judged,
                             final Set<String> parameters, final Set<String> rowParameters,
                             final Map<String, Object> constants, final ExpressionEvaluator typer) {
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
            if (candidates > 1 && typer != null) {
                reached = typedOverload(node, CalledRoutineSecrets.overloads(executor, parts), written, typer);
                candidates = reached == null ? candidates : 1;
            }
            if (candidates == 1) {
                final Set<String> rowRead = rowReadParameters(node, reached, parameters, rowParameters);
                requireEvaluableBody(node, reached, executor, rowRead);
                final Map<String, Object> bound = constantArguments(node, reached, executor, constants);
                if (judged.add(Integer.toHexString(System.identityHashCode(reached)) + new TreeSet<String>(rowRead)
                        + new TreeMap<String, Object>(bound))) {
                    requireSourcesOf(reached, executor, judged, rowRead, bound);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            walk(node.getChild(i), executor, judged, parameters, rowParameters, constants, typer);
        }
    }

    /**
     * The constant values a call passes its function's parameters, by parameter: an argument written of literals, of
     * the built-in functions over them and of the calling body's own constant parameters, by place or by name, and the
     * default of a parameter the call leaves out. The account expands the body with them while the statement
     * compiles, so the objects its GET_DDL calls name through a parameter are looked up then (live-verified).
     */
    private static Map<String, Object> constantArguments(final ParseTree call, final Function function,
                                                         final QueryExecutor executor,
                                                         final Map<String, Object> constants) {
        final Map<String, Object> bound = new HashMap<String, Object>();
        if (function.getUdfLanguage() != UdfLanguage.SQL || function.getBody() == null) {
            return bound;
        }
        final List<Parameter> declared = function.getParameters();
        final List<ParseTree> positional = new ArrayList<ParseTree>();
        final List<FrostlakeParser.NamedArgumentContext> named = new ArrayList<FrostlakeParser.NamedArgumentContext>();
        argumentsOf(call, positional, named);
        for (int i = 0; i < positional.size() && i < declared.size(); i++) {
            bindConstant(bound, declared.get(i), positional.get(i), executor, constants);
        }
        for (final FrostlakeParser.NamedArgumentContext argument : named) {
            final String name = SqlIdentifiers.canonical(argument.variableName());
            for (final Parameter parameter : declared) {
                if (parameter.getName().equalsIgnoreCase(name) && argument.expression() != null) {
                    bindConstant(bound, parameter, argument.expression(), executor, constants);
                }
            }
        }
        for (final Parameter parameter : declared) {
            final String name = parameter.getName().toUpperCase(Locale.ROOT);
            if (!bound.containsKey(name) && parameter.hasDefault() && parameter.getDefaultValue() != null) {
                final Object value = constantValue(parameter.getDefaultValue(), null, executor,
                    Collections.<String, Object>emptyMap());
                if (value != null) {
                    bound.put(name, value);
                }
            }
        }
        return bound;
    }

    /** Binds a parameter to its argument's constant value, where the argument is a constant. */
    private static void bindConstant(final Map<String, Object> bound, final Parameter parameter,
                                     final ParseTree argument, final QueryExecutor executor,
                                     final Map<String, Object> constants) {
        final Object value = constantValue(ParseTreeText.getOriginalText((ParserRuleContext) argument), argument,
            executor, constants);
        if (value != null) {
            bound.put(parameter.getName().toUpperCase(Locale.ROOT), value);
        }
    }

    /**
     * An expression's value where it is a constant: written of literals, of the built-in functions that answer alike
     * each time, and of the names {@code constants} binds; null for anything else, a value that reads a row or a
     * user-defined function among them.
     *
     * @param text     the expression's text
     * @param tree     its parse tree, or null to judge it by its text alone
     * @param executor the executor compiling the statement
     * @param constants the calling body's constant parameters
     */
    private static Object constantValue(final String text, final ParseTree tree, final QueryExecutor executor,
                                        final Map<String, Object> constants) {
        try {
            final ParseTree written = tree != null ? tree : AntlrExpressionParser.parseTree(text);
            final Expression parsed = ExpressionEvaluator.parse(text);
            if (!builtInsOnly(written, executor) || VolatileCalls.writes(parsed)) {
                return null;
            }
            final ExpressionEvaluator evaluator = new ExpressionEvaluator(null, executor.getFunctionRegistry(),
                executor.getCatalog(), executor);
            evaluator.setOuterLateralContext(constants);
            return DeferredFault.read(evaluator.evaluate(parsed, new Row(new ArrayList<Object>())));
        } catch (final RuntimeException notConstant) {
            return null;
        }
    }

    /** Whether every call a tree writes is a built-in function's, and it holds no query of its own. */
    private static boolean builtInsOnly(final ParseTree node, final QueryExecutor executor) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionNameContext && executor.getFunctionRegistry()
                .getFunction(node.getText().toUpperCase(Locale.ROOT)) == null) {
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (!builtInsOnly(node.getChild(i), executor)) {
                return false;
            }
        }
        return true;
    }

    /** A call's arguments: those written by place, and those written by name. */
    private static void argumentsOf(final ParseTree call, final List<ParseTree> positional,
                                    final List<FrostlakeParser.NamedArgumentContext> named) {
        if (call instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionArgListContext arguments =
                ((FrostlakeParser.FunctionCallExprContext) call).functionArgList();
            if (arguments != null) {
                positional.addAll(arguments.functionArg());
            }
        } else if (call instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            positional.addAll(((FrostlakeParser.FunctionCallMixedArgsExprContext) call).expression());
            named.addAll(((FrostlakeParser.FunctionCallMixedArgsExprContext) call).namedArgument());
        } else if (call instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            named.addAll(((FrostlakeParser.FunctionCallNamedArgsExprContext) call).namedArgumentList()
                .namedArgument());
        }
    }

    /**
     * The parameters of a call's function whose arguments read the row (see {@link #walk}): a positional argument
     * binds the parameter of its place, and a named one the parameter of its name.
     */
    private static Set<String> rowReadParameters(final ParseTree call, final Function function,
                                                 final Set<String> parameters, final Set<String> rowParameters) {
        final List<ParseTree> positional = new ArrayList<ParseTree>();
        final List<FrostlakeParser.NamedArgumentContext> named = new ArrayList<FrostlakeParser.NamedArgumentContext>();
        argumentsOf(call, positional, named);
        final Set<String> rowRead = new HashSet<String>();
        final List<Parameter> declared = function.getParameters();
        for (int i = 0; i < positional.size() && i < declared.size(); i++) {
            if (readsAColumn(positional.get(i), parameters, rowParameters)) {
                rowRead.add(declared.get(i).getName().toUpperCase(Locale.ROOT));
            }
        }
        for (final FrostlakeParser.NamedArgumentContext argument : named) {
            final String name = SqlIdentifiers.canonical(argument.variableName());
            for (final Parameter parameter : declared) {
                if (parameter.getName().equalsIgnoreCase(name) && readsAColumn(argument, parameters, rowParameters)) {
                    rowRead.add(parameter.getName().toUpperCase(Locale.ROOT));
                }
            }
        }
        return rowRead;
    }

    /**
     * A SQL function whose body is a query is expanded into the statement as the scalar subquery it is, each
     * parameter read from the call's argument, and a body that is an expression brings its own scalar subqueries:
     * where an argument reads the row, such a query is a subquery correlated through that parameter, and a shape the
     * account cannot evaluate correlated is refused as written inside the function, however the call passes its
     * arguments, by place or by name. {@code SELECT a + 1 WHERE a > 0} called over a column is "Unsupported subquery
     * type cannot be evaluated inside Function object: SQ_WHERE", as {@code (SELECT a + 1 WHERE a > 0) * 10} is
     * inside its own function, and over a constant both answer (live-verified).
     */
    private static void requireEvaluableBody(final ParseTree call, final Function function,
                                             final QueryExecutor executor, final Set<String> rowRead) {
        if (CalledRoutineSecrets.calledBy(call) == null || function.getUdfLanguage() != UdfLanguage.SQL
                || function.isTableFunction() || function.getBody() == null || rowRead.isEmpty() || typedOnly(call)) {
            return;
        }
        final String body = SqlUdfBodyFrame.executableBody(function.getBody());
        if (body == null) {
            return;
        }
        final List<FrostlakeParser.SelectStatementContext> queries =
            new ArrayList<FrostlakeParser.SelectStatementContext>();
        if (executor.queryStatementOf(body.trim()) != null) {
            final FrostlakeParser.SelectStatementContext statement = executor.subqueryStatement(body.trim());
            if (statement != null) {
                queries.add(statement);
            }
        } else {
            try {
                scalarSubqueries(AntlrExpressionParser.parseTree(body.trim()), queries);
            } catch (final RuntimeException noExpression) {
                // A block's statements are judged as they run.
                return;
            }
        }
        // The parameters the call passed no row's value hold one value for the whole statement.
        final Set<String> fixed = new HashSet<String>();
        for (final Parameter parameter : function.getParameters()) {
            final String name = parameter.getName().toUpperCase(Locale.ROOT);
            if (!rowRead.contains(name)) {
                fixed.add(name);
            }
        }
        for (final FrostlakeParser.SelectStatementContext query : queries) {
            if (new CorrelatedSubqueryRule(executor, rowRead).withFixedNames(fixed).refusesScalar(query)) {
                throw UnsupportedSubqueryException.inside(function);
            }
        }
    }

    /** The scalar subqueries of an expression, the outermost of each nest. */
    private static void scalarSubqueries(final ParseTree node,
                                         final List<FrostlakeParser.SelectStatementContext> queries) {
        if (node instanceof FrostlakeParser.ScalarSubqueryExprContext) {
            queries.add(((FrostlakeParser.ScalarSubqueryExprContext) node).selectStatement());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            scalarSubqueries(node.getChild(i), queries);
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

    /**
     * Whether an argument names a column anywhere in it. Inside a function's body a name that is one of its
     * parameters is the value the call passed, which reads a row only where the call's argument did.
     */
    private static boolean readsAColumn(final ParseTree node, final Set<String> parameters,
                                        final Set<String> rowParameters) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts =
                ParseTreeText.qualifiedNameParts(((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            return parts.length != 1 || !parameters.contains(parts[0]) || rowParameters.contains(parts[0]);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsAColumn(node.getChild(i), parameters, rowParameters)) {
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

    /**
     * The one overload a call's positional arguments settle by their declared types, or null where none or several
     * do: an exact number and the bare NULL reach a number parameter, a text a text one, and any other kind settles
     * nothing.
     */
    private static Function typedOverload(final ParseTree call, final List<Function> overloads, final int written,
                                          final ExpressionEvaluator typer) {
        final List<ParseTree> positional = new ArrayList<ParseTree>();
        final List<FrostlakeParser.NamedArgumentContext> named = new ArrayList<FrostlakeParser.NamedArgumentContext>();
        argumentsOf(call, positional, named);
        if (!named.isEmpty() || positional.size() != written) {
            return null;
        }
        final List<String> families = new ArrayList<String>();
        for (final ParseTree argument : positional) {
            final String family = argumentFamily(argument, typer);
            if (family == null) {
                return null;
            }
            families.add(family);
        }
        Function settled = null;
        for (final Function overload : overloads) {
            if (!takes(overload, written) || !acceptsFamilies(overload, families)) {
                continue;
            }
            if (settled != null) {
                return null;
            }
            settled = overload;
        }
        return settled;
    }

    /** The family an argument's declared type gives it for an overload's choice: FIXED, TEXT, NULL, or null. */
    private static String argumentFamily(final ParseTree argument, final ExpressionEvaluator typer) {
        final Expression parsed;
        final DataType type;
        try {
            parsed = ExpressionEvaluator.parse(argument instanceof ParserRuleContext
                ? ParseTreeText.getOriginalText((ParserRuleContext) argument) : argument.getText());
            if (parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.NULL) {
                return "NULL";
            }
            type = typer.inferStaticType(parsed);
        } catch (final RuntimeException untyped) {
            return null;
        }
        return parameterFamily(type);
    }

    private static String parameterFamily(final DataType type) {
        if (type instanceof NumericType && !NumericType.isApproximate(type)) {
            return "FIXED";
        }
        return type instanceof StringType ? "TEXT" : null;
    }

    /** Whether an overload's parameters take arguments of these families, the bare NULL reaching a number. */
    private static boolean acceptsFamilies(final Function overload, final List<String> families) {
        final List<Parameter> parameters = overload.getParameters();
        for (int i = 0; i < families.size(); i++) {
            final String parameter = parameterFamily(parameters.get(i).getDataType());
            final String argument = families.get(i);
            if (parameter == null || !parameter.equals("NULL".equals(argument) ? "FIXED" : argument)) {
                return false;
            }
        }
        return true;
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
     * The objects the body's GET_DDL calls name with constant text, or through a parameter the call passed constant
     * text, looked up where the function lives as the call looks them up while a statement compiles: a missing one
     * refuses the statement wherever the function is called, under SYSTEM$TYPEOF, in a branch never taken and over no
     * row alike (live-verified).
     */
    private static void requireDdlObjectsIn(final ParseTree node, final QueryExecutor executor,
                                            final Map<String, Object> constants) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            final List<FrostlakeParser.FunctionArgContext> arguments = call.functionArgList() == null
                ? new ArrayList<FrostlakeParser.FunctionArgContext>() : call.functionArgList().functionArg();
            if ("GET_DDL".equalsIgnoreCase(call.functionName().getText()) && arguments.size() >= 2
                    && arguments.size() <= 3) {
                final String type = constantText(arguments.get(0), executor, constants);
                final String name = constantText(arguments.get(1), executor, constants);
                final BuiltInFunction getDdl = executor.getFunctionRegistry().getFunction("GET_DDL");
                if (type != null && name != null && getDdl instanceof GetDdl) {
                    ((GetDdl) getDdl).requireObject(type, name);
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            requireDdlObjectsIn(node.getChild(i), executor, constants);
        }
    }

    /**
     * The text a call's argument writes as a string literal, or the text a constant argument, a call's parameter
     * among its names, comes to; null for any other argument.
     */
    private static String constantText(final FrostlakeParser.FunctionArgContext argument,
                                       final QueryExecutor executor, final Map<String, Object> constants) {
        if (argument.booleanExpr() == null) {
            return null;
        }
        try {
            final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(argument.booleanExpr()));
            if (parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getType() == LiteralType.STRING) {
                return String.valueOf(((LiteralExpression) parsed).getValue());
            }
        } catch (final RuntimeException notALiteral) {
            return null;
        }
        if (constants.isEmpty()) {
            return null;
        }
        final Object value = constantValue(ParseTreeText.getOriginalText(argument.booleanExpr()),
            argument.booleanExpr(), executor, constants);
        return value instanceof String ? (String) value : null;
    }

    /**
     * Judges the calls of a function's body with the parameters its call passed a row's value. Where the body is a
     * query, a call inside it that the account cannot evaluate correlated is refused as the function itself: the
     * query is the subquery the call expands into, so {@code SELECT w(o.k) FROM o} over {@code w(a) AS 'SELECT
     * sq_where(a) * 10'} names W, where an expression body leaves the name of the function it calls
     * (live-verified).
     */
    private static void walkBody(final ParseTree parsed, final Function function, final QueryExecutor executor,
                                 final Set<String> judged, final Set<String> rowRead,
                                 final Map<String, Object> constants) {
        final Set<String> parameters = new HashSet<String>();
        for (final Parameter parameter : function.getParameters()) {
            parameters.add(parameter.getName().toUpperCase(Locale.ROOT));
        }
        try {
            walk(parsed, executor, judged, parameters, rowRead, constants, null);
        } catch (final UnsupportedSubqueryException refused) {
            if (!rowRead.isEmpty() && refused.namesFunction()
                    && parsed instanceof FrostlakeParser.SqlScriptContext) {
                throw UnsupportedSubqueryException.inside(function);
            }
            throw refused;
        }
    }

    /** The relations a SQL function's body reads, looked for where the function lives, and those of its calls. */
    private static void requireSourcesOf(final Function function, final QueryExecutor executor,
                                         final Set<String> judged, final Set<String> rowRead,
                                         final Map<String, Object> constants) {
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
                    requireDdlObjectsIn(parsed, executor, constants);
                    executor.expandViewsIn(parsed);
                    walkBody(parsed, function, executor, judged, rowRead, constants);
                } finally {
                    ViewExpansion.end(outer);
                }
                return null;
            }
        });
    }
}
