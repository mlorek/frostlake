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

import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snowflake's compile-time check on the SELECT list of a grouped query, applied to a plain
 * {@code GROUP BY <expression-list>} and to the super-group forms (ROLLUP / CUBE / GROUPING SETS,
 * whose caller passes the UNION of every grouping set's keys — live treats a column in ANY set as
 * grouped and refuses one in none with the same sentence as the plain form, including a column
 * reached through a {@code GROUPING()} argument):
 *
 * <ul>
 *   <li>a select item that reads a base-table COLUMN which is neither aggregated nor a grouping key is
 *       rejected with {@code '<TABLE>.<COLUMN>' in select clause is neither an aggregate nor in the group
 *       by clause.};</li>
 *   <li>a select item that references the alias of a LATER item of the same list — including its own, and
 *       the mutually recursive {@code b AS a, a AS b} — is rejected with {@code invalid identifier '<NAME>'}.
 *       Only EARLIER aliases are visible (the lateral-alias rule the engine already implements), and a real
 *       column always wins over a same-named alias, which is exactly why the first rule fires for it.</li>
 * </ul>
 *
 * <p>The check is deliberately one-sided: a false REJECTION breaks a query the engine could run, while a
 * missed rejection only leaves Frostlake more permissive than Snowflake. So everything whose provenance
 * cannot be established from the parse tree is ACCEPTED — subqueries, window calls, lambda bodies,
 * unresolvable identifiers — and {@code GROUP BY ALL} is not validated at all (its caller never
 * reaches here). A star item IS validated: each expanded column must be grouped, and a miss is
 * rejected at live's sentinel position (line 0, position -1) since nobody wrote the reference. Any
 * unexpected failure inside the validator abandons validation for the statement rather than failing it.
 */
final class GroupBySelectListValidator {

    /**
     * Date/time part names that appear as a BARE identifier in an argument position —
     * {@code DATEADD(day, 1, x)}, {@code DATE_PART(month, x)}, {@code DATEDIFF(week, a, b)}. They parse as
     * ordinary column references, so a table with a column of the same name would otherwise be reported as
     * ungrouped. Skipping them costs only a missed rejection.
     */
    private static final Set<String> DATE_PART_WORDS = new HashSet<>(Arrays.asList(
        "YEAR", "YEARS", "Y", "YY", "YYY", "YYYY", "YR", "YRS", "YEAROFWEEK", "YEAROFWEEKISO",
        "QUARTER", "QUARTERS", "Q", "QTR", "QTRS",
        "MONTH", "MONTHS", "MM", "MON", "MONS",
        "WEEK", "WEEKS", "W", "WK", "WEEKOFYEAR", "WOY", "WY", "WEEKISO", "WEEKOFYEARISO",
        "DAY", "DAYS", "D", "DD", "DAYOFMONTH", "DAYOFWEEK", "DAYOFWEEKISO", "DAYOFYEAR", "YEARDAY", "DOW",
        "DOY", "DW", "DY",
        "HOUR", "HOURS", "H", "HH", "HR", "HRS",
        "MINUTE", "MINUTES", "M", "MI", "MIN", "MINS",
        "SECOND", "SECONDS", "S", "SEC", "SECS",
        "MILLISECOND", "MILLISECONDS", "MS", "MSEC", "MSECS", "MILLISECS",
        "MICROSECOND", "MICROSECONDS", "US", "USEC", "USECS", "MICROSECS",
        "NANOSECOND", "NANOSECONDS", "NS", "NSEC", "NSECS", "NANOSEC", "NSECONDS", "NANOSECS",
        "EPOCH_SECOND", "EPOCH_MILLISECOND", "EPOCH_MICROSECOND", "EPOCH_NANOSECOND",
        "EPOCH", "TIMEZONE_HOUR", "TIMEZONE_MINUTE", "TZH", "TZM"));

    private final QueryExecutor executor;
    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final List<Table> allTables;

    /** Canonical AST prints of every grouping-key form (raw, ordinal-resolved, alias-resolved, expanded). */
    private final Set<String> keyPrints = new HashSet<>();
    /** The same forms as whitespace-stripped upper-case text, a fallback for what the AST cannot parse. */
    private final Set<String> keyTexts = new HashSet<>();
    /** Bare column names among the grouping keys ({@code GROUP BY o.city} contributes {@code CITY}). */
    private final Set<String> keyColumns = new HashSet<>();

    /** Aliases of the items BEFORE the one being walked — the only ones it may reference. */
    private final Set<String> earlierAliases = new HashSet<>();
    /** Aliases of the item being walked and every item after it — referencing one is a forward reference. */
    private final Set<String> laterAliases = new HashSet<>();

    /** The first violation's message, or null while the list is still valid. */
    private String rejection;

    /**
     * Whether the walk is currently INSIDE a window call. Live holds a window's own references to the
     * grouping — its arguments and its OVER keys alike — and answers with the BRACKETED sentence when
     * one is not grouped, even where an explicit GROUP BY would otherwise produce the select-clause
     * wording. Measured on the account:
     *
     * <pre>
     *   … ROW_NUMBER() OVER (ORDER BY c)      GROUP BY a    [G.C] is not a valid group by expression
     *   … ROW_NUMBER() OVER (PARTITION BY c…) GROUP BY a    the same
     *   … LAG(c) OVER (ORDER BY a)            GROUP BY a    the same — an ARGUMENT counts too
     *   … SUM(b) OVER (ORDER BY a)            GROUP BY a    [G.B] — a windowed aggregate is not an
     *                                                       aggregate for this purpose
     *   … LAG(MAX(c)) OVER (ORDER BY a)       GROUP BY a    reads: the nested aggregate settles it
     * </pre>
     */
    private boolean insideWindowCall;

    /**
     * Whether this run validates IMPLICIT aggregation (an aggregate or HAVING with no GROUP BY):
     * the same walk applies with an empty key set, but the refusal is live's OTHER family —
     * {@code [<OWNER>.<COLUMN>] is not a valid group by expression}, bracketed, positionless, with
     * identifiers spelled canonically (quoted when not plain upper-case, which is also what puts
     * the {@code "values"} moniker on an unaliased derived table).
     */
    private final boolean implicitAggregation;

    GroupBySelectListValidator(final QueryExecutor executor, final Table table,
                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        this(executor, table, aliasToTable, allTables, false);
    }

    GroupBySelectListValidator(final QueryExecutor executor, final Table table,
                               final Map<String, Table> aliasToTable, final List<Table> allTables,
                               final boolean implicitAggregation) {
        this.executor = executor;
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.allTables = allTables;
        this.implicitAggregation = implicitAggregation;
    }

    /**
     * Validate the SELECT list of a grouped query. {@code aliasNames} is 1:1 with
     * {@code ctx.selectList().selectItem()} (null where an item carries no alias); {@code groupKeyForms}
     * holds every textual form of every grouping key — the source text plus its ordinal-, alias- and
     * nested-alias-resolved rewrites — so an item matches whichever spelling the key was written in.
     * With {@code validateWhenKeyless} the check also runs over an EMPTY key union (a grouping-sets
     * query whose only set is {@code ()} groups by nothing, so every bare column is ungrouped);
     * without it, an empty {@code groupKeyForms} means the caller collected nothing and validation
     * is abandoned rather than rejecting the whole list.
     */
    void validate(final FrostlakeParser.SelectClauseContext ctx, final List<String> aliasNames,
                  final List<String> groupKeyForms, final boolean validateWhenKeyless) {
        if (groupKeyForms.isEmpty() && !validateWhenKeyless) {
            return;
        }
        for (final String form : groupKeyForms) {
            keyTexts.add(squash(form));
            final String print = canonicalPrint(form);
            if (print != null) {
                keyPrints.add(print);
            }
            final String bare = bareColumnName(form);
            if (bare != null) {
                keyColumns.add(bare);
            }
        }
        final List<FrostlakeParser.SelectItemContext> items = ctx.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            final ParserRuleContext itemExpr = SelectItemAccessors.getItemExpression(items.get(i));
            if (itemExpr == null) {
                // A star projects each of its columns, and live holds every one to the same rule as
                // a written reference; {@code {*}} builds ONE object and is left alone.
                checkStarItem(items.get(i));
                if (rejection != null) {
                    throw new RuntimeException(rejection);
                }
                continue;
            }
            earlierAliases.clear();
            laterAliases.clear();
            for (int a = 0; a < aliasNames.size() && a < items.size(); a++) {
                if (aliasNames.get(a) == null) {
                    continue;
                }
                if (a < i) {
                    earlierAliases.add(aliasNames.get(a).toUpperCase());
                } else {
                    laterAliases.add(aliasNames.get(a).toUpperCase());
                }
            }
            try {
                checkNode(itemExpr);
            } catch (final RuntimeException unexpected) {
                return;   // never fail a query because the validator itself stumbled
            }
            if (rejection != null) {
                throw new RuntimeException(rejection);
            }
        }
        if (ctx.qualifyClause() != null) {
            // QUALIFY's own window calls are held to the grouping exactly as the select list's are
            // ({@code GROUP BY a QUALIFY ROW_NUMBER() OVER (ORDER BY c) = 1} is "[G.C] is not a valid
            // group by expression"). Only its WINDOWS are walked here — that is the measured case, and
            // the rest of a QUALIFY predicate has its own resolution rules. Every select alias is
            // referencable from QUALIFY, so none of them counts as a forward reference.
            earlierAliases.clear();
            laterAliases.clear();
            for (final String alias : aliasNames) {
                if (alias != null) {
                    earlierAliases.add(alias.toUpperCase());
                }
            }
            checkWindowsWithin(ctx.qualifyClause());
            if (rejection != null) {
                throw new RuntimeException(rejection);
            }
        }
    }

    /** Runs the grouped walk over every window call inside {@code node}, leaving the rest alone. */
    private void checkWindowsWithin(final ParseTree node) {
        if (node == null || rejection != null) {
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (isWindowCall(node)) {
            checkNode(node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkWindowsWithin(node.getChild(i));
        }
    }

    // ── the walk ─────────────────────────────────────────────────────────────

    /**
     * Walk one select item, stopping at every subtree whose provenance is settled: a grouping key, an
     * aggregate or window call, a subquery, a lambda. Everything else is descended into until a bare
     * column reference is reached.
     */
    private void checkNode(final ParseTree node) {
        if (node == null || rejection != null) {
            return;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.LambdaFunctionContext) {
            return;   // a subquery's / lambda's names resolve in their own scope
        }
        if ((node instanceof FrostlakeParser.ExpressionContext || node instanceof FrostlakeParser.BooleanExprContext)
                && matchesGroupingKey((ParserRuleContext) node)) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            checkColumnReference((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        if (isWindowCall(node)) {
            // A window's references are NOT settled by the window: they must be grouped like any
            // other. Descend, remembering where we are so the refusal takes the bracketed form; a
            // nested aggregate still settles its own subtree on the way down.
            final boolean enclosing = insideWindowCall;
            insideWindowCall = true;
            try {
                for (int i = 0; i < node.getChildCount(); i++) {
                    checkNode(node.getChild(i));
                }
            } finally {
                insideWindowCall = enclosing;
            }
            return;
        }
        if (isAggregateOrWindowCall(node)) {
            return;   // an aggregate's argument is per-row, whatever the grouping
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            checkNode(node.getChild(i));
        }
    }

    /** One bare (possibly qualified) column reference, classified against the columns and the aliases. */
    private void checkColumnReference(final FrostlakeParser.QualifiedNameExprContext ref) {
        final String[] parts = ParseTreeText.qualifiedNameParts(ref.qualifiedName());
        final String last = parts[parts.length - 1].toUpperCase();
        if (parts.length > 1) {
            // Only a name whose immediate qualifier is a relation in scope is a column reference; anything
            // else is a path INTO a column (a VARIANT field) or a qualifier this validator cannot see.
            final String qualifier = parts[parts.length - 2];
            if (namesATable(qualifier) && namesAColumn(last) && !keyColumns.contains(last)) {
                if (implicitAggregation || insideWindowCall) {
                    final List<String> rawParts = rawNameParts(ref);
                    rejectImplicit(spellWrittenIdentifier(rawParts.get(rawParts.size() - 2)) + "."
                        + spellWrittenIdentifier(rawParts.get(rawParts.size() - 1)));
                } else {
                    reject(ref, "'" + qualifier.toUpperCase() + "." + last
                        + "' in select clause is neither an aggregate nor in the group by clause.");
                }
            }
            return;
        }
        if (DATE_PART_WORDS.contains(last) && isBareFunctionArgument(ref)) {
            return;   // DATEADD(day, …) — a unit keyword, not this table's DAY column
        }
        if (namesAColumn(last)) {
            // A real column outranks a same-named alias, so it must be grouped or aggregated.
            if (!keyColumns.contains(last)) {
                if (implicitAggregation || insideWindowCall) {
                    final List<String> rawParts = rawNameParts(ref);
                    rejectImplicit(owningNameSpelled(last) + "."
                        + spellWrittenIdentifier(rawParts.get(rawParts.size() - 1)));
                } else {
                    reject(ref, "'" + owningName(last) + "." + last
                        + "' in select clause is neither an aggregate nor in the group by clause.");
                }
            }
            return;
        }
        if (earlierAliases.contains(last)) {
            return;   // the legal lateral-alias reference
        }
        if (laterAliases.contains(last)) {
            reject(ref, "invalid identifier '" + last + "'");
        }
        // Anything else — an outer/correlated reference, a session variable, a name this validator cannot
        // see — is left to the evaluator.
    }

    private void reject(final ParserRuleContext at, final String detail) {
        rejection = SqlCompilationError.at(at.getStart().getLine(),
            at.getStart().getCharPositionInLine(), detail);
    }

    /**
     * A star item's expanded columns, each held to the grouped-select rule. An expanded column
     * carries no source position — nobody wrote it — so the refusal reports live's sentinel
     * (line 0, position -1), spelling the column with the star's own qualifier: the owning table
     * for a bare {@code *}, the written qualifier for {@code t.*}.
     */
    private void checkStarItem(final FrostlakeParser.SelectItemContext item) {
        final boolean qualified = SelectItemAccessors.isQualifiedStarItem(item);
        if (!SelectItemAccessors.isStarItem(item) && !qualified) {
            return;   // {*} builds ONE object; it is not a per-column projection
        }
        final List<StarColumn> columns;
        try {
            columns = executor.starItemColumns(item, table, aliasToTable);
        } catch (final RuntimeException resolveFailure) {
            return;   // an unresolvable star is the evaluator's problem, not the validator's
        }
        final String prefix = qualified ? SelectItemAccessors.getItemQualifier(item) + "." : "";
        for (final StarColumn sc : columns) {
            final String name = sc.getSourceName().toUpperCase();
            if (!sc.getExpression().equalsIgnoreCase(prefix + sc.getSourceName())) {
                continue;   // a REPLACE'd column projects an expression, not the column itself
            }
            if (namesAColumn(name) && !keyColumns.contains(name)) {
                if (implicitAggregation) {
                    final String owner = qualified
                        ? SelectItemAccessors.getItemQualifier(item).toUpperCase()
                        : owningNameSpelled(name);
                    rejectImplicit(owner + "." + spellResolvedName(sc.getSourceName()));
                    return;
                }
                final String owner = qualified
                    ? SelectItemAccessors.getItemQualifier(item).toUpperCase() : owningName(name);
                rejection = SqlCompilationError.at(0, -1, "'" + owner + "." + name
                    + "' in select clause is neither an aggregate nor in the group by clause.");
                return;
            }
        }
    }

    /** The reference's parts as WRITTEN — quoted parts keep their quotes. */
    private static List<String> rawNameParts(final FrostlakeParser.QualifiedNameExprContext ref) {
        final List<String> parts = new ArrayList<String>();
        parts.add(ref.qualifiedName().nameStartPart().getText());
        for (final FrostlakeParser.NamePartContext np : ref.qualifiedName().namePart()) {
            parts.add(np.getText());
        }
        return parts;
    }

    /** The implicit-aggregation refusal — bracketed, positionless, live's other family. */
    private void rejectImplicit(final String reference) {
        rejection = SqlCompilationError.of("[" + reference + "] is not a valid group by expression");
    }

    /**
     * An identifier as WRITTEN, spelled the way the implicit-aggregation message spells it: a
     * quoted token stays verbatim with its quotes, an unquoted one folds to upper case.
     */
    private static String spellWrittenIdentifier(final String rawToken) {
        // The RESOLVED name, not the written one: live prints [KW.A] whether the item was written A,
        // "A", kw."A" or "KW"."A", and keeps quotes only for a name that needs them ([Q2."a"]).
        return SqlIdentifiers.spellCanonical(SqlIdentifiers.canonicalText(rawToken));
    }

    /**
     * A RESOLVED name (a table's or column's stored identity) spelled canonically: plain
     * upper-case names stay bare, anything else — a quoted-created column, or the {@code values}
     * moniker of an unaliased derived table — is quoted.
     */
    private static String spellResolvedName(final String name) {
        return SqlIdentifiers.spellCanonical(name);
    }

    /** The owning relation of an unqualified column, spelled for the implicit-aggregation message. */
    private String owningNameSpelled(final String column) {
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue().hasColumn(column)) {
                    // An unaliased derived table goes by the internal "values" moniker, which the
                    // message spells quoted; a written alias folds to upper case.
                    return "values".equals(entry.getKey()) ? "\"values\"" : entry.getKey().toUpperCase();
                }
            }
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate != null && candidate.hasColumn(column) && candidate.getName() != null) {
                    return spellResolvedName(candidate.getName());
                }
            }
        }
        return table != null && table.getName() != null ? spellResolvedName(table.getName()) : column;
    }

    // ── classification helpers ───────────────────────────────────────────────

    /** True when this subexpression IS one of the grouping keys, however the key was spelled. */
    private boolean matchesGroupingKey(final ParserRuleContext node) {
        final String text = ParseTreeText.getOriginalText(node);
        if (keyTexts.contains(squash(text))) {
            return true;
        }
        final String print = canonicalPrint(text);
        return print != null && keyPrints.contains(print);
    }

    /**
     * True for a call whose arguments say nothing about this query's grouping: an aggregate (its argument
     * is per-row), a window call (computed after grouping), or a function the engine does not know — a
     * UDF, or a Snowflake aggregate this build has no entry for, either of which could be an aggregate.
     */

    /** Whether the node is a function call carrying an {@code OVER} clause. */
    private boolean isWindowCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            return ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null;
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallNamedArgsExprContext) node).overClause() != null;
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            return ((FrostlakeParser.FunctionCallMixedArgsExprContext) node).overClause() != null;
        }
        return false;
    }

    private boolean isAggregateOrWindowCall(final ParseTree node) {
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            return isOpaqueCall(((FrostlakeParser.FunctionCallStarExprContext) node).functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call = (FrostlakeParser.FunctionCallExprContext) node;
            return call.overClause() != null || isOpaqueCall(call.functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallNamedArgsExprContext) {
            final FrostlakeParser.FunctionCallNamedArgsExprContext call =
                (FrostlakeParser.FunctionCallNamedArgsExprContext) node;
            return call.overClause() != null || isOpaqueCall(call.functionName());
        }
        if (node instanceof FrostlakeParser.FunctionCallMixedArgsExprContext) {
            final FrostlakeParser.FunctionCallMixedArgsExprContext call =
                (FrostlakeParser.FunctionCallMixedArgsExprContext) node;
            return call.overClause() != null || isOpaqueCall(call.functionName());
        }
        return false;
    }

    private boolean isOpaqueCall(final FrostlakeParser.FunctionNameContext name) {
        if (name == null) {
            return true;
        }
        final String called = name.getText().toUpperCase();
        return executor.getFunctionRegistry().hasAggregateFunction(called)
            || !executor.getFunctionRegistry().hasFunction(called);
    }

    /** True when the reference sits directly in a function's argument list, where a bare unit word lives. */
    private static boolean isBareFunctionArgument(final FrostlakeParser.QualifiedNameExprContext ref) {
        final ParseTree parent = ref.getParent();
        if (parent instanceof FrostlakeParser.ValueExprContext) {
            return parent.getParent() instanceof FrostlakeParser.FunctionArgContext;
        }
        return parent instanceof FrostlakeParser.FunctionCallMixedArgsExprContext
            || parent instanceof FrostlakeParser.NamedArgumentContext;
    }

    private boolean namesAColumn(final String name) {
        if (table != null && table.hasColumn(name)) {
            return true;
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate != null && candidate.hasColumn(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean namesATable(final String name) {
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate != null && name.equalsIgnoreCase(candidate.getName())) {
                    return true;
                }
            }
        }
        return table != null && name.equalsIgnoreCase(table.getName());
    }

    /** The name Snowflake prefixes an ungrouped column with: its FROM alias, or its table's name. */
    private String owningName(final String column) {
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue().hasColumn(column)) {
                    return entry.getKey().toUpperCase();
                }
            }
        }
        if (allTables != null) {
            for (final Table candidate : allTables) {
                if (candidate != null && candidate.hasColumn(column) && candidate.getName() != null) {
                    return candidate.getName().toUpperCase();
                }
            }
        }
        return table != null && table.getName() != null ? table.getName().toUpperCase() : column;
    }

    // ── text canonicalisation ────────────────────────────────────────────────

    private static String canonicalPrint(final String text) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(text);
            return AstPrinterVisitor.print(parsed).toUpperCase();
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    private static String bareColumnName(final String text) {
        try {
            final Expression parsed = ExpressionEvaluator.parse(text);
            if (parsed instanceof ColumnReferenceExpression) {
                return ((ColumnReferenceExpression) parsed).getColumnName().toUpperCase();
            }
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
        return null;
    }

    /** Upper-cased with every whitespace character removed — a spelling-tolerant text identity. */
    private static String squash(final String text) {
        final StringBuilder squashed = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (!Character.isWhitespace(c)) {
                squashed.append(Character.toUpperCase(c));
            }
        }
        return squashed.toString();
    }
}
