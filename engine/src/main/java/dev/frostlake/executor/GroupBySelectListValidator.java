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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snowflake's compile-time check on the SELECT list of a grouped query, applied to a PLAIN
 * {@code GROUP BY <expression-list>}:
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
 * cannot be established from the parse tree is ACCEPTED — star items, subqueries, window calls, lambda
 * bodies, unresolvable identifiers — and {@code GROUP BY ALL} / ROLLUP / CUBE / GROUPING SETS are not
 * validated at all (their callers never reach here). Any unexpected failure inside the validator abandons
 * validation for the statement rather than failing it.
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

    GroupBySelectListValidator(final QueryExecutor executor, final Table table,
                               final Map<String, Table> aliasToTable, final List<Table> allTables) {
        this.executor = executor;
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.allTables = allTables;
    }

    /**
     * Validate the SELECT list of a plain grouped query. {@code aliasNames} is 1:1 with
     * {@code ctx.selectList().selectItem()} (null where an item carries no alias); {@code groupKeyForms}
     * holds every textual form of every grouping key — the source text plus its ordinal-, alias- and
     * nested-alias-resolved rewrites — so an item matches whichever spelling the key was written in.
     */
    void validate(final FrostlakeParser.SelectClauseContext ctx, final List<String> aliasNames,
                  final List<String> groupKeyForms) {
        if (groupKeyForms.isEmpty()) {
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
                continue;   // a star / qualified-star / braced-star item projects columns wholesale
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
        if (isAggregateOrWindowCall(node)) {
            return;   // an aggregate's argument is per-row, a window's is computed after grouping
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
                reject(ref, "'" + qualifier.toUpperCase() + "." + last
                    + "' in select clause is neither an aggregate nor in the group by clause.");
            }
            return;
        }
        if (DATE_PART_WORDS.contains(last) && isBareFunctionArgument(ref)) {
            return;   // DATEADD(day, …) — a unit keyword, not this table's DAY column
        }
        if (namesAColumn(last)) {
            // A real column outranks a same-named alias, so it must be grouped or aggregated.
            if (!keyColumns.contains(last)) {
                reject(ref, "'" + owningName(last) + "." + last
                    + "' in select clause is neither an aggregate nor in the group by clause.");
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
