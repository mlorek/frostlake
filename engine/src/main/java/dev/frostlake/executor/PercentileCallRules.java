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

import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.NumericType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The COMPILE-time rules a {@code PERCENTILE_CONT} / {@code PERCENTILE_DISC} call must satisfy. Every
 * one of these was ACCEPTED before — most answering NULL, which is the quiet-wrong-answer shape rather
 * than a refusal:
 *
 * <pre>
 *   PERCENTILE_CONT(0.5)                              Function … requires a WITHIN GROUP clause
 *   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY a, b)   containing a single order by element.
 *   PERCENTILE_CONT(2)   …                            Invalid parameter value: 2. Reason: …
 *   PERCENTILE_CONT(&lt;column&gt;) …                       argument 1 … needs to be constant, found …
 *   PERCENTILE_CONT(0.5, 0.5) …                       Invalid argument types for function …
 *   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY nosuchcol)   invalid identifier 'NOSUCHCOL'
 * </pre>
 *
 * <p>★ A MISSING CLAUSE AND A TWO-ELEMENT ONE ARE ONE REFUSAL, not two — the same sentence covers
 * both, which is why the rule is written as "exactly one element" rather than as two checks.
 *
 * <p>★ THE ARGUMENT NUMBER IS NOT THE WRITTEN POSITION. A column reference is "argument 1" and a
 * COMPUTED expression is "argument 0", measured both ways. That reads like an accident of which stage
 * refuses, and it is reproduced as measured rather than tidied — live's plan puts the ordered value
 * FIRST and the fraction second (its nested-aggregate echo prints
 * {@code PERCENTILE_CONT(CAST(… AS …), 0.5)}), so "argument 1" is the fraction's place in the PLAN
 * while "argument 0" is its place as written.
 *
 * <p>★ THE RANGE REFUSAL CARRIES NO COMPILATION PREFIX. It is a parameter-value complaint, in the
 * family that reports a bad value rather than a bad statement, and it echoes the fraction exactly as
 * the user wrote it.
 *
 * <p>★ MEDIAN IS HERE TOO, for one rule only. It is the 0.5 percentile spelled as a plain call, so its
 * ARGUMENT is the ordering key and shares the key's TYPE rule — {@code MEDIAN(&lt;DATE&gt;)} is refused
 * with the same incompatible-types sentence. None of the clause, fraction or arity rules reach it.
 */
final class PercentileCallRules {

    /** The two ordered percentiles, which share every rule here. */
    private static final String CONTINUOUS = "PERCENTILE_CONT";
    private static final String DISCRETE = "PERCENTILE_DISC";

    /** MEDIAN, which shares only the ordering key's TYPE rule — it has no WITHIN GROUP to judge. */
    private static final String MEDIAN = "MEDIAN";

    /** The numeric type an incompatible-types sentence quotes — a constant, never derived. */
    private static final String NUMERIC_COUNTERPART = "NUMBER(9,0)";

    private final QueryExecutor executor;
    private final FunctionRegistry functionRegistry;
    private final Catalog catalog;

    PercentileCallRules(final QueryExecutor executor, final FunctionRegistry functionRegistry,
                        final Catalog catalog) {
        this.executor = executor;
        this.functionRegistry = functionRegistry;
        this.catalog = catalog;
    }

    /**
     * Judge every percentile call under {@code node}.
     *
     * @param node any parse-tree node; the walk covers the statement
     * @param table the leading relation, for typing the arguments
     * @param aliasToTable the FROM's alias map, or null
     * @param allTables every relation in the FROM, or null
     */
    void validate(final ParseTree node, final Table table, final Map<String, Table> aliasToTable,
                  final List<Table> allTables) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call =
                (FrostlakeParser.FunctionCallExprContext) node;
            final String name = SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase();
            if (CONTINUOUS.equals(name) || DISCRETE.equals(name)) {
                validateCall(call, name, table, aliasToTable, allTables);
            } else if (MEDIAN.equals(name)) {
                validateMedianCall(call, table, aliasToTable, allTables);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            validate(node.getChild(i), table, aliasToTable, allTables);
        }
    }

    /**
     * The names the sibling SELECT items publish, which the key may legitimately use — the WITHIN GROUP
     * clause reaches the select list the way an ordinary aggregate argument does. Exempting them here
     * keeps the identifier check from refusing a name that resolves; a real column of the same name is
     * unaffected, since it resolves on its own.
     */
    private Set<String> siblingSelectAliases(final FrostlakeParser.FunctionCallExprContext call) {
        final Set<String> names = new HashSet<>();
        FrostlakeParser.SelectListContext selectList = null;
        for (ParseTree node = call; node != null; node = node.getParent()) {
            if (node instanceof FrostlakeParser.SelectListContext) {
                selectList = (FrostlakeParser.SelectListContext) node;
                break;
            }
        }
        if (selectList == null) {
            return names;
        }
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias != null) {
                names.add(SqlIdentifiers.canonicalText(alias));
            }
        }
        return names;
    }

    /**
     * MEDIAN is the 0.5 percentile spelled as a plain call, so its ARGUMENT is the ordering key and
     * carries the same type rule — a temporal one is refused at compile time with the very sentence the
     * percentiles produce. Nothing else here applies: MEDIAN has no WITHIN GROUP clause and no fraction
     * to judge, and its arity is already the ordinary one.
     */
    private void validateMedianCall(final FrostlakeParser.FunctionCallExprContext call,
                                    final Table table, final Map<String, Table> aliasToTable,
                                    final List<Table> allTables) {
        final List<FrostlakeParser.BooleanExprContext> args =
            ParseTreeText.functionBooleanArgs(call.functionArgList());
        if (args.size() != 1) {
            return;
        }
        rejectTemporalKey(call, args.get(0), table, aliasToTable, allTables);
    }

    private void validateCall(final FrostlakeParser.FunctionCallExprContext call, final String name,
                              final Table table, final Map<String, Table> aliasToTable,
                              final List<Table> allTables) {
        final FrostlakeParser.OrderByClauseContext withinGroup =
            AggregateFunctions.withinGroupOrderBy(call);
        if (withinGroup == null || withinGroup.orderItem().size() != 1) {
            throw new RuntimeException(SqlCompilationError.of("Function " + name
                + " requires a WITHIN GROUP clause containing a single order by element."));
        }
        // ★ THE KEY'S OWN NAMES. An unresolvable identifier inside WITHIN GROUP was never walked, so
        // it was a SCOPE HOLE rather than a wording difference: the call answered NULL over a column
        // that does not exist. An invalid identifier outranks every other complaint here, as it does
        // everywhere else, so it is judged first.
        executor.validateClauseScope(
            ParseTreeText.getOriginalText(withinGroup.orderItem(0).expression()),
            table, aliasToTable, allTables, siblingSelectAliases(call),
            withinGroup.orderItem(0).expression());
        rejectTemporalKey(call, withinGroup.orderItem(0).expression(), table, aliasToTable, allTables);
        final List<FrostlakeParser.BooleanExprContext> args =
            ParseTreeText.functionBooleanArgs(call.functionArgList());
        if (args.size() > 1) {
            throw new RuntimeException(SqlCompilationError.at(
                call.getStart().getLine(), call.getStart().getCharPositionInLine(),
                "Invalid argument types for function '" + name + "': ("
                    + argumentTypes(args, table, aliasToTable, allTables) + ")"));
        }
        if (args.isEmpty()) {
            // The arity refusal is the ordinary one and already agrees; nothing further to judge.
            return;
        }
        final String written = ParseTreeText.getOriginalText(args.get(0)).trim();
        final BigDecimal fraction = constantFraction(written);
        // A fraction of the WRONG TYPE is an argument-type complaint, and that one speaks first:
        // PERCENTILE_CONT(<OBJECT column>) is "Invalid argument types for function 'PERCENTILE_CONT':
        // (OBJECT)" live, not a complaint that the object is not constant. So a non-numeric argument
        // is left to the walk that already refuses it.
        if (fraction == null && !isNumericArgument(args.get(0), table, aliasToTable, allTables)) {
            return;
        }
        if (fraction == null) {
            // A COLUMN is argument 1 and a computed expression argument 0 — live's own two numbers,
            // and the echo follows suit: a column is printed as the plan resolves it, an expression
            // as it was written.
            final boolean bareName = written.matches("[A-Za-z_$][A-Za-z_$0-9]*(\\.[A-Za-z_$][A-Za-z_$0-9]*)*");
            throw new RuntimeException(SqlCompilationError.of("argument " + (bareName ? "1" : "0")
                + " to function " + name + " needs to be constant, found '"
                + (bareName ? new PlanEcho(table, aliasToTable, allTables, functionRegistry, catalog)
                    .print(args.get(0)) : written) + "'"));
        }
        if (fraction.compareTo(BigDecimal.ZERO) < 0 || fraction.compareTo(BigDecimal.ONE) > 0) {
            // No compilation prefix: a bad VALUE, not a bad statement.
            throw new RuntimeException("Invalid parameter value: " + written
                + ". Reason: percentile must be between 0 and 1");
        }
    }

    /**
     * A TEMPORAL ordering key, which live refuses at COMPILE time — an interpolating aggregate is
     * defined over numbers and a date is not one, so the two types simply do not meet:
     *
     * <pre>
     *   WITHIN GROUP (ORDER BY &lt;DATE&gt;)            incompatible types: [DATE] and [NUMBER(9,0)]
     *   WITHIN GROUP (ORDER BY &lt;TIMESTAMP_NTZ&gt;)   incompatible types: [TIMESTAMP_NTZ(9)] and [NUMBER(9,0)]
     * </pre>
     *
     * <p>The {@code NUMBER(9,0)} is a CONSTANT — the same one the other incompatible-type sentences
     * quote — not a width derived from anything in the statement.
     *
     * <p>PERCENTILE_DISC is refused too, though it only picks a value and never does arithmetic on it:
     * the rule is about the argument's type, not about what the function goes on to do with it. MEDIAN
     * reaches the same sentence through its single argument.
     *
     * <p>A non-numeric key that is NOT temporal is left alone here: a VARCHAR one is refused by live at
     * ROW time, with the ordinary numeric-conversion sentence, and belongs to that surface instead.
     */
    private void rejectTemporalKey(final FrostlakeParser.FunctionCallExprContext call,
                                   final ParserRuleContext key, final Table table,
                                   final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // A key that mentions a sibling SELECT ALIAS means that alias's DEFINING expression, and is
        // typed through it — ANYWHERE in the key, not only when the key IS the alias. A real COLUMN of
        // that name outranks the alias, so those names are left alone.
        final String keyType = declaredTypeText(
            withSiblingAliases(call, key, table, allTables), table, aliasToTable, allTables);
        // TIME is spelled with its precision — TIME(9) — and a BINARY key is refused in the same
        // sentence (live-verified); a BOOLEAN one is an internal error on the account and is left alone.
        if (keyType != null && (keyType.equals("DATE") || keyType.startsWith("TIME")
                || keyType.startsWith("BINARY"))) {
            throw new RuntimeException(SqlCompilationError.of(
                "incompatible types: [" + keyType + "] and [" + NUMERIC_COUNTERPART + "]"));
        }
    }

    /** Whether a name belongs to a relation in the FROM, which outranks any select-list alias. */
    private boolean namesAColumn(final String name, final Table table, final List<Table> allTables) {
        if (table != null && table.hasColumn(name)) {
            return true;
        }
        if (allTables != null) {
            for (final Table other : allTables) {
                if (other != null && other.hasColumn(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** An expression's declared type as live names it, or null when it does not resolve. */
    private String declaredTypeText(final String expr, final Table table,
                                    final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator typer = new ExpressionEvaluator(table, functionRegistry, catalog, executor);
        if (allTables != null && !allTables.isEmpty()) {
            typer.setMultiTableContext(aliasToTable, allTables);
        }
        try {
            return typer.argumentTypeText(ExpressionEvaluator.parse(expr));
        } catch (final RuntimeException notTypeable) {
            return null;
        }
    }

    /**
     * The key's text with every sibling SELECT ALIAS it mentions expanded to that alias's defining
     * expression. Matching the WHOLE key against a whole alias was not enough: it typed
     * {@code MEDIAN(dd)} through the alias but left {@code MEDIAN(DATEADD(day, 1, dd))} holding an
     * unresolvable name, and a temporal function over an argument it cannot type falls back to
     * TIMESTAMP_NTZ where live names the DATE the alias really carries.
     *
     * <p>An alias whose name is also a real COLUMN is skipped, because the column outranks it.
     */
    private String withSiblingAliases(final FrostlakeParser.FunctionCallExprContext call,
                                      final ParserRuleContext key, final Table table,
                                      final List<Table> allTables) {
        final String written = ParseTreeText.getOriginalText(key).trim();
        FrostlakeParser.SelectListContext selectList = null;
        for (ParseTree node = call; node != null; node = node.getParent()) {
            if (node instanceof FrostlakeParser.SelectListContext) {
                selectList = (FrostlakeParser.SelectListContext) node;
                break;
            }
        }
        if (selectList == null) {
            return written;
        }
        final List<String> names = new ArrayList<>();
        final List<String> definitions = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final String alias = SelectItemAccessors.getItemAlias(item);
            if (alias == null) {
                continue;
            }
            final String canonical = SqlIdentifiers.canonicalText(alias);
            if (namesAColumn(canonical, table, allTables)) {
                continue;
            }
            names.add(canonical);
            definitions.add(ParseTreeText.getOriginalText(SelectItemAccessors.getItemValueExpr(item)));
        }
        return SiblingAliasSubstitution.applied(written, names, definitions);
    }

    /** Whether an argument's DECLARED type is numeric — an object, an array or a string is not. */
    private boolean isNumericArgument(final FrostlakeParser.BooleanExprContext arg, final Table table,
                                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator typer = new ExpressionEvaluator(table, functionRegistry, catalog, executor);
        if (allTables != null && !allTables.isEmpty()) {
            typer.setMultiTableContext(aliasToTable, allTables);
        }
        try {
            return typer.inferStaticType(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(arg))) instanceof NumericType;
        } catch (final RuntimeException notTypeable) {
            return true;   // untypeable: judge it as written, which is what the constant rule does
        }
    }

    /** The fraction when the argument is a bare numeric literal, else null. */
    private BigDecimal constantFraction(final String written) {
        try {
            return new BigDecimal(written);
        } catch (final NumberFormatException notALiteral) {
            return null;
        }
    }

    /** The call's argument types as live lists them, comma-separated. */
    private String argumentTypes(final List<FrostlakeParser.BooleanExprContext> args, final Table table,
                                 final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final ExpressionEvaluator typer = new ExpressionEvaluator(table, functionRegistry, catalog, executor);
        if (allTables != null && !allTables.isEmpty()) {
            typer.setMultiTableContext(aliasToTable, allTables);
        }
        final List<String> names = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext arg : args) {
            try {
                // The PARAMETERIZED spelling the refusals use — a 0.5 literal is NUMBER(2,1), not a
                // bare NUMBER, and every other argument-type sentence names it that way.
                names.add(typer.argumentTypeText(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(arg))));
            } catch (final RuntimeException notTypeable) {
                names.add("NULL");
            }
        }
        return String.join(", ", names);
    }
}
