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
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.RoutineOverloads;
import dev.frostlake.executor.RoutineSecretsCheck;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.executor.expressions.DateTimeUnitSlot;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.ParsedEchoPrinter;
import dev.frostlake.executor.expressions.SqlUdfBodyFrame;
import dev.frostlake.metastore.model.Function;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UdfLanguage;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.SqlTypeNames;
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The user-defined functions a DEFAULT expression calls, judged as the account judges them (live-verified).
 *
 * <p>A <b>column's</b> DEFAULT may call a SQL function whose body is an expression: the account expands the body
 * into the default, so {@code a NUMBER DEFAULT f1()} is created and fills the column. What the expansion may not
 * hold, the default may not call:
 *
 * <pre>
 *   DEFAULT jsf()          a JavaScript, Python, Java or Scala function, or a Snowflake Scripting one
 *       SQL compilation error: Invalid column default expression [JSF()]
 *   DEFAULT fjs()          a SQL function whose body calls one: the expanded body is echoed
 *       SQL compilation error: Invalid column default expression [JSF() + 1]
 *   DEFAULT fq()           a SQL function whose body aggregates, as a query body's MAX(x) does
 *       SQL compilation error: error in line 1 position 45: While expanding UDF in default expression:
 *       SQL compilation error: error line 1 at position 8 aggregate functions are not allowed as part of the
 *       specification of a default value clause.
 * </pre>
 *
 * <p>The refused default is echoed as the plan prints it, a function under its bare name in upper case whatever
 * schema the default wrote before it. ALTER TABLE ADD COLUMN, which takes a literal alone, echoes a default that
 * calls a function the same way, and refuses a name that is no function before that.
 *
 * <p>A <b>routine parameter's</b> DEFAULT calls no user-defined function at all, a SQL one included:
 * {@code invalid default argument expression F1()}, placed where the expression stands - at an operator for an
 * operation - and a bare name there is an invalid identifier, a parameter's own name too.
 */
final class DefaultExpressionCalls {

    /** How deep a SQL function's body is expanded into the functions it calls. */
    private static final int MAX_EXPANSION_DEPTH = 16;

    private final QueryExecutor queryExecutor;

    DefaultExpressionCalls(final QueryExecutor queryExecutor) {
        this.queryExecutor = queryExecutor;
    }

    /**
     * Refuse a column DEFAULT that calls a user-defined function its expansion cannot hold. A call with more
     * arguments than any overload takes is refused first, where it stands: {@code too many arguments for function
     * [F1(1)] expected 0, got 1}.
     *
     * @param written   the DEFAULT's expression
     * @param mismatched what to refuse the default with when a function it cannot hold answers a type the column
     *                   does not take, which the account judges first; null when the type fits
     */
    void requireColumnDefault(final FrostlakeParser.ExpressionContext written, final RuntimeException mismatched) {
        final List<FrostlakeParser.FunctionCallExprContext> calls = callsUnder(written);
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final Function function = called(call);
            if (function == null) {
                continue;
            }
            // A function that cannot use its secrets is refused by its name, whatever the default makes of it.
            RoutineSecretsCheck.require(queryExecutor.getCatalog(), function.getEnvironment());
            refuseTooManyArguments(call);
            if (!expandsAsSql(function)) {
                if (mismatched != null) {
                    throw mismatched;
                }
                throw invalidColumnDefault(defaultEcho(written, calls));
            }
            final String body = SqlUdfBodyFrame.executableBody(function.getBody()).trim();
            final ParseTree parsed = parsedBody(body);
            if (parsed == null) {
                continue;
            }
            final Token aggregate = firstAggregate(parsed);
            if (aggregate != null) {
                final Token at = call.getStart();
                throw new RuntimeException(SqlCompilationError.of("error in line " + at.getLine() + " position "
                    + at.getCharPositionInLine() + ": While expanding UDF in default expression: "
                    + SqlCompilationError.at(aggregate.getLine(),
                        aggregate.getCharPositionInLine() + (aggregate.getLine() == 1 ? 1 : 0),
                        "aggregate functions are not allowed as part of the specification of a default value"
                            + " clause.")));
            }
            if (callsUnexpandable(parsed, 1)) {
                // The body stands where the call stood: what is echoed is the default the expansion made.
                final String whole = ParseTreeText.getOriginalText(written);
                final String own = ParseTreeText.getOriginalText(call);
                final String expanded = whole.equals(own) ? body : whole.replace(own, "(" + body + ")");
                throw invalidColumnDefault(planText(expanded));
            }
        }
    }

    /**
     * What ALTER TABLE ADD COLUMN echoes of a DEFAULT that is no literal: the default as the plan prints it, a
     * user-defined function under its bare name.
     *
     * @param written the DEFAULT's expression
     * @return the echo, or null where the plan's printer does not read the default
     */
    String addColumnEcho(final FrostlakeParser.ExpressionContext written) {
        return printedPlan(barelyNamed(written));
    }

    /**
     * Refuse a routine parameter's DEFAULT that calls a user-defined function or reads a name.
     *
     * @param written the DEFAULT's expression
     */
    void requireParameterDefault(final ParserRuleContext written) {
        requireKnownFunctions(written);
        final Token query = firstOfType(written, FrostlakeParser.SELECT);
        if (query != null) {
            // A default reads no query: it is refused where the query starts, echoed as the tree holds it.
            throw new RuntimeException(SqlCompilationError.at(query.getLine(), query.getCharPositionInLine(),
                "invalid default argument expression " + parsedText(ParseTreeText.getOriginalText(written))));
        }
        final FrostlakeParser.QualifiedNameExprContext name = firstName(written);
        if (name != null) {
            final Token at = name.getStart();
            final StringBuilder spelled = new StringBuilder();
            for (final String part : SqlIdentifiers.canonicalTextParts(name.getText())) {
                spelled.append(spelled.length() > 0 ? "." : "").append(SqlIdentifiers.spellCanonical(part));
            }
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "invalid identifier '" + spelled + "'"));
        }
        if (!callsUserFunction(written)) {
            return;
        }
        final Token at = placeOf(written);
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "invalid default argument expression " + planText(barelyNamed(written))));
    }

    /**
     * A routine parameter's DEFAULT as the account tells it back in the routine's DDL: printed from the plan the
     * default compiled to, so a cast written {@code ::} is {@code CAST(… AS …)}, a NULL is {@code null}, a bare
     * {@code CURRENT_DATE} is {@code CURRENT_DATE()} and an array written in brackets is {@code ARRAY_CONSTRUCT(…)}
     * (live-verified).
     *
     * @param written the DEFAULT's expression
     * @return the default as told
     */
    String toldParameterDefault(final ParserRuleContext written) {
        final String expression = ParseTreeText.getOriginalText(written);
        try {
            final ExpressionEvaluatorVisitor printer = new ExpressionEvaluatorVisitor(
                new Table("$DEFAULT", new ArrayList<TableColumn>(), true), null, queryExecutor.getFunctionRegistry(),
                queryExecutor.getCatalog());
            return printer.strictToldDefaultText(AntlrExpressionParser.parse(expression));
        } catch (final RuntimeException unprintable) {
            return expression;
        }
    }

    /**
     * Refuses a parameter's default of type BOOLEAN that the parameter's type does not take (live-verified): a
     * predicate — a comparison, a LIKE, an IN, a BETWEEN, an IS — given to any parameter but a BOOLEAN one, and TRUE
     * or FALSE given to one of neither BOOLEAN nor text, placed at the predicate's operator: {@code p NUMBER DEFAULT
     * 1 IN (1, 2)} is "default argument data type BOOLEAN does not match expected data type NUMBER(38,0)" at the IN.
     * A NOT, an AND and an OR are taken by a text parameter.
     *
     * @param written   the default
     * @param parameter the parameter it is given to
     */
    static void requireDefaultType(final FrostlakeParser.BooleanExprContext written, final Parameter parameter) {
        final DataType type = parameter.getDataType();
        if (type instanceof BooleanType || !(written instanceof FrostlakeParser.ValueExprContext)) {
            return;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) written).expression();
        final boolean predicate = value instanceof FrostlakeParser.ComparisonExprContext
            || value instanceof FrostlakeParser.DoubledEqualsExprContext
            || value instanceof FrostlakeParser.LikeExprContext || value instanceof FrostlakeParser.RlikeExprContext
            || value instanceof FrostlakeParser.LikeAnyAllExprContext
            || value instanceof FrostlakeParser.LikeAnyAllSingleExprContext
            || value instanceof FrostlakeParser.InListExprContext || value instanceof FrostlakeParser.BetweenExprContext
            || value instanceof FrostlakeParser.IsNullExprContext
            || value instanceof FrostlakeParser.IsDistinctExprContext;
        final boolean truth = value instanceof FrostlakeParser.LiteralExprContext
            && (value.getStart().getType() == FrostlakeParser.TRUE
                || value.getStart().getType() == FrostlakeParser.FALSE)
            && !(type instanceof StringType);
        if (!predicate && !truth) {
            return;
        }
        final Token at = predicate && value.getChild(1) instanceof TerminalNode
            ? ((TerminalNode) value.getChild(1)).getSymbol() : value.getStart();
        final String expected = type instanceof StringType && parameter.isUnsized()
            ? "VARCHAR(" + DataTypeParser.CAST_STRING_DEFAULT + ")" : SqlTypeNames.canonical(type);
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "default argument data type BOOLEAN does not match expected data type " + expected));
    }

    /** Refuse a call that writes more arguments than the widest overload of its function takes. */
    private void refuseTooManyArguments(final FrostlakeParser.FunctionCallExprContext call) {
        final List<String> parts = new ArrayList<String>();
        for (final FrostlakeParser.IdentifierContext part : call.functionName().identifier()) {
            parts.add(SqlIdentifiers.canonical(part));
        }
        final int written = call.functionArgList() == null ? 0 : call.functionArgList().functionArg().size();
        int widest = -1;
        for (final Function overload : overloads(parts)) {
            if (!overload.isTableFunction()) {
                widest = Math.max(widest, overload.getParameters().size());
            }
        }
        if (widest < 0 || written <= widest) {
            return;
        }
        final String own = ParseTreeText.getOriginalText(call);
        final String bare = own.substring(call.functionName().identifier(parts.size() - 1).getStart().getStartIndex()
            - call.getStart().getStartIndex());
        final Token at = call.getStart();
        throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            "too many arguments for function [" + planText(bare) + "] expected " + widest + ", got " + written));
    }

    private static RuntimeException invalidColumnDefault(final String echo) {
        return new RuntimeException(SqlCompilationError.of("Invalid column default expression [" + echo + "]"));
    }

    /** Where an expression stands for a refusal: an operation at its operator, anything else where it begins. */
    private static Token placeOf(final ParserRuleContext written) {
        if (written instanceof FrostlakeParser.ValueExprContext) {
            return placeOf(((FrostlakeParser.ValueExprContext) written).expression());
        }
        final boolean operation = written.getChildCount() == 3 && written.getChild(1) instanceof TerminalNode
            && written.getChild(0) instanceof ParserRuleContext && written.getChild(2) instanceof ParserRuleContext;
        if (operation) {
            return ((TerminalNode) written.getChild(1)).getSymbol();
        }
        return written.getStart();
    }

    /**
     * Refuse a call whose name is no function, built in or user-defined.
     *
     * @param written the expression whose calls are judged
     */
    void requireKnownFunctions(final ParserRuleContext written) {
        final ExpressionEvaluator scan = new ExpressionEvaluator(null, queryExecutor.getFunctionRegistry(),
            queryExecutor.getCatalog(), queryExecutor);
        final List<FrostlakeParser.FunctionCallExprContext> calls = callsUnder(written);
        final List<FunctionCallExpression> unknown = new ArrayList<FunctionCallExpression>();
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final Expression parsed;
            try {
                parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(call));
            } catch (final RuntimeException notACallOnItsOwn) {
                continue;
            }
            if (parsed instanceof FunctionCallExpression && !scan.resolvesToAFunctionName(parsed)) {
                unknown.add((FunctionCallExpression) parsed);
            }
        }
        if (!unknown.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(scan.unknownFunctionSentence(unknown)));
        }
    }

    private boolean callsUserFunction(final ParseTree tree) {
        final List<FrostlakeParser.FunctionCallExprContext> calls = callsUnder(tree);
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            if (called(call) != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether a SQL function's body, expanded, reaches a function no default can hold. */
    private boolean callsUnexpandable(final ParseTree body, final int depth) {
        final List<FrostlakeParser.FunctionCallExprContext> calls = callsUnder(body);
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final Function function = called(call);
            if (function == null) {
                continue;
            }
            if (!expandsAsSql(function) || depth >= MAX_EXPANSION_DEPTH) {
                return true;
            }
            final ParseTree inner = parsedBody(SqlUdfBodyFrame.executableBody(function.getBody()).trim());
            if (inner != null && callsUnexpandable(inner, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a function is a SQL function whose body is an expression or a query, which a default expands. */
    private boolean expandsAsSql(final Function function) {
        if (function.getUdfLanguage() != UdfLanguage.SQL || function.getBody() == null) {
            return false;
        }
        return !queryExecutor.isProceduralBlock(SqlUdfBodyFrame.executableBody(function.getBody()).trim());
    }

    /** A SQL body's parse tree: an expression's, or a query's; null for a body the grammar reads as neither. */
    private ParseTree parsedBody(final String body) {
        try {
            final ParseTree query = queryExecutor.queryStatementOf(body);
            if (query != null) {
                return query;
            }
        } catch (final RuntimeException notAQuery) {
            // an expression, then
        }
        try {
            return AntlrExpressionParser.parseTree(body);
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /** The first aggregate call of a body, in source order; null when it has none. */
    private Token firstAggregate(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            if (call.functionName() != null && queryExecutor.getFunctionRegistry().hasAggregateFunction(
                    call.functionName().getText().toUpperCase(Locale.ROOT))) {
                return call.getStart();
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstAggregate(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The first name an expression reads as a value, in source order; null when it reads none. The word in a
     * date/time unit slot names a unit and is no such name: {@code DATEADD(day, 1, d)} reads no DAY.
     */
    private static FrostlakeParser.QualifiedNameExprContext firstName(final ParseTree node) {
        return firstNameOutside(node, null);
    }

    /** {@link #firstName} under a node, the subtree of the unit slot of the call around it left out. */
    private static FrostlakeParser.QualifiedNameExprContext firstNameOutside(final ParseTree node,
                                                                             final ParseTree unit) {
        if (node == unit) {
            return null;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return (FrostlakeParser.QualifiedNameExprContext) node;
        }
        final ParseTree own = unitSlotOf(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.QualifiedNameExprContext found =
                firstNameOutside(node.getChild(i), own != null ? own : unit);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The argument standing in a call's date/time unit slot, or null for a node that is no such call. */
    private static ParseTree unitSlotOf(final ParseTree node) {
        if (!(node instanceof FrostlakeParser.FunctionCallExprContext)) {
            return null;
        }
        final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
        if (call.functionName() == null) {
            return null;
        }
        final int slot = DateTimeUnitSlot.positionIn(call.functionName().getText().toUpperCase(Locale.ROOT));
        final List<FrostlakeParser.BooleanExprContext> args = ParseTreeText.functionBooleanArgs(call.functionArgList());
        return slot >= 0 && slot < args.size() ? args.get(slot) : null;
    }

    /** The first token of a type under a tree, in source order; null when it holds none. */
    private static Token firstOfType(final ParseTree node, final int type) {
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).getSymbol().getType() == type ? ((TerminalNode) node).getSymbol() : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstOfType(node.getChild(i), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** An expression's text as the tree it parses to prints it; as written where the printer does not read it. */
    private static String parsedText(final String expression) {
        try {
            return ParsedEchoPrinter.print(ExpressionEvaluator.parse(expression));
        } catch (final RuntimeException unprintable) {
            return expression;
        }
    }

    /** Every call under the node, in source order. */
    private static List<FrostlakeParser.FunctionCallExprContext> callsUnder(final ParseTree node) {
        final List<FrostlakeParser.FunctionCallExprContext> calls =
            new ArrayList<FrostlakeParser.FunctionCallExprContext>();
        collect(node, calls);
        return calls;
    }

    private static void collect(final ParseTree node, final List<FrostlakeParser.FunctionCallExprContext> calls) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            calls.add((FrostlakeParser.FunctionCallExprContext) node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), calls);
        }
    }

    /**
     * The user-defined function a call names, by its name and the number of its arguments; null for a call of a
     * built-in function and for a name no user-defined function has.
     */
    private Function called(final FrostlakeParser.FunctionCallExprContext call) {
        if (call.functionName() == null || call.functionName().identifier().isEmpty()) {
            return null;
        }
        final List<String> parts = new ArrayList<String>();
        for (final FrostlakeParser.IdentifierContext part : call.functionName().identifier()) {
            parts.add(SqlIdentifiers.canonical(part));
        }
        final String simple = parts.get(parts.size() - 1);
        if (parts.size() == 1 && (queryExecutor.getFunctionRegistry().hasFunction(simple.toUpperCase(Locale.ROOT))
                || queryExecutor.getFunctionRegistry().hasAggregateFunction(simple.toUpperCase(Locale.ROOT)))) {
            return null;
        }
        final int written = call.functionArgList() == null ? 0 : call.functionArgList().functionArg().size();
        Function first = null;
        for (final Function overload : overloads(parts)) {
            if (overload.isTableFunction()) {
                continue;
            }
            if (overload.getParameters().size() == written) {
                return overload;
            }
            first = first == null ? overload : first;
        }
        return first;
    }

    /** The user-defined function overloads a call's name reaches (see {@link RoutineOverloads#functions}). */
    private List<Function> overloads(final List<String> parts) {
        return RoutineOverloads.functions(queryExecutor, parts);
    }

    /**
     * An expression's text with each user-defined function under its bare name, as the plan names it:
     * {@code test_schema.f1() + 1} reads {@code f1() + 1}.
     */
    private String barelyNamed(final ParserRuleContext written) {
        final String text = ParseTreeText.getOriginalText(written);
        final int origin = written.getStart().getStartIndex();
        final List<FrostlakeParser.FunctionCallExprContext> calls = callsUnder(written);
        final StringBuilder bare = new StringBuilder();
        int cursor = 0;
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final List<FrostlakeParser.IdentifierContext> parts = call.functionName() == null
                ? new ArrayList<FrostlakeParser.IdentifierContext>() : call.functionName().identifier();
            if (parts.size() < 2 || called(call) == null) {
                continue;
            }
            final int from = call.functionName().getStart().getStartIndex() - origin;
            final int to = parts.get(parts.size() - 1).getStart().getStartIndex() - origin;
            if (from < cursor || to > text.length()) {
                continue;
            }
            bare.append(text, cursor, from);
            cursor = to;
        }
        return bare.append(text, cursor, text.length()).toString();
    }

    /**
     * A column default as the refusal of a function it cannot hold echoes it: the plan's spelling, a conversion to
     * FLOAT as TO_DOUBLE, and the call of a Snowflake Scripting function as the conversion of its answer to the number
     * it declares — {@code [TO_NUMBER(FBLK())]}, {@code [CASE_FLATTENED((TO_DOUBLE(TO_NUMBER(FBLK()))) >
     * (TO_DOUBLE(0.5)), 1, 0)]} (live-verified); as written where the plan's printer does not read it.
     */
    private String defaultEcho(final FrostlakeParser.ExpressionContext written,
                               final List<FrostlakeParser.FunctionCallExprContext> calls) {
        final Map<String, DataType> converted = new HashMap<>();
        for (final FrostlakeParser.FunctionCallExprContext call : calls) {
            final Function function = called(call);
            if (function != null && function.getUdfLanguage() == UdfLanguage.SQL && function.getBody() != null
                    && !expandsAsSql(function) && function.getReturnType() instanceof NumericType
                    && !NumericType.isApproximate(function.getReturnType())) {
                converted.put(function.getName().toUpperCase(Locale.ROOT), function.getReturnType());
            }
        }
        final String text = barelyNamed(written);
        try {
            final ExpressionEvaluatorVisitor printer = new ExpressionEvaluatorVisitor(
                new Table("$DEFAULT", new ArrayList<TableColumn>(), true), null, queryExecutor.getFunctionRegistry(),
                queryExecutor.getCatalog());
            return printer.strictDefaultEchoText(AntlrExpressionParser.parse(text), converted);
        } catch (final RuntimeException unprintable) {
            return text;
        }
    }

    /** An expression's text as the plan prints it; as written where the plan's printer does not read it. */
    private String planText(final String expression) {
        final String printed = printedPlan(expression);
        return printed != null ? printed : expression;
    }

    /** An expression's text as the plan prints it, or null where the plan's printer does not read it. */
    private String printedPlan(final String expression) {
        try {
            final ExpressionEvaluatorVisitor printer = new ExpressionEvaluatorVisitor(
                new Table("$DEFAULT", new ArrayList<TableColumn>(), true), null, queryExecutor.getFunctionRegistry(),
                queryExecutor.getCatalog());
            return printer.strictPlanText(AntlrExpressionParser.parse(expression));
        } catch (final RuntimeException unprintable) {
            return null;
        }
    }
}
