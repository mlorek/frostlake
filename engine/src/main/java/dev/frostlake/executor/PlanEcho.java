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

import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.executor.expressions.UnsupportedFeatureException;
import dev.frostlake.functions.FunctionRegistry;
import dev.frostlake.functions.window.BooleanArgumentWindowFunctions;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import dev.frostlake.types.DataType;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;

/**
 * An expression as Snowflake NAMES it in a refusal: re-printed from the analysed plan rather than
 * echoed from the source text. Every column carries the relation it came from — the name that
 * relation is known by IN SCOPE, so its alias where one was written, its own name otherwise, and a
 * derived table's or a CTE's alias just the same — while keyword and identifier case and all
 * original spacing are normalised away.
 *
 * <p>A WINDOW call gets more than that. Its sort keys carry a direction and a null placement even
 * when neither was typed:
 *
 * <pre>
 *   OVER (ORDER BY n)                    OVER (ORDER BY EB.N ASC NULLS LAST)
 *   OVER (ORDER BY n DESC)               OVER (ORDER BY EB.N DESC NULLS FIRST)
 *   OVER (ORDER BY n NULLS FIRST)        OVER (ORDER BY EB.N ASC NULLS FIRST)
 *   OVER (PARTITION BY (g) ORDER BY n)   OVER (PARTITION BY EB.G ORDER BY EB.N ASC NULLS LAST)
 * </pre>
 *
 * <p>So ASC defaults to NULLS LAST and DESC to NULLS FIRST — the same defaults the sort itself uses, the
 * other way round under DEFAULT_NULL_ORDERING = 'FIRST' (see {@link SessionNullOrdering}) — and the
 * direction is printed whether or not it was written. A PARTITION BY key takes neither, and
 * its parentheses are dropped. Three things present in the source do NOT survive into a window echo:
 * the FRAME ({@code ROWS BETWEEN …}, {@code RANGE …}), the null treatment
 * ({@code IGNORE NULLS} / {@code RESPECT NULLS}), and all original spacing. DISTINCT does survive.
 *
 * <p>What live prints that this cannot: its echo comes from a TYPE-CHECKED plan, so implicit casts
 * appear that are nowhere in the source and a CASE arrives rewritten as {@code CASE_FLATTENED}.
 * Expressions of that shape stay divergent here.
 */
final class PlanEcho {

    private final ExpressionEvaluator printer;

    PlanEcho(final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables,
             final FunctionRegistry functionRegistry, final Catalog catalog) {
        this(table, aliasToTable, allTables, functionRegistry, catalog, null);
    }

    /**
     * An echo that also re-prints a subquery among the call's arguments from its plan, which takes the executor
     * that plans it: {@code MAX((SELECT MAX(g.v) FROM g))} is told as
     * {@code MAX((SELECT MAX(G.V) AS "MAX(G.V)" FROM G AS G))} (live-verified). Without one a subquery prints as
     * written.
     */
    PlanEcho(final Table table, final Map<String, Table> aliasToTable, final List<Table> allTables,
             final FunctionRegistry functionRegistry, final Catalog catalog, final QueryExecutor queryExecutor) {
        this.printer = queryExecutor == null ? new ExpressionEvaluator(table, functionRegistry, catalog)
            : new ExpressionEvaluator(table, functionRegistry, catalog, queryExecutor);
        if (allTables != null) {
            this.printer.setMultiTableContext(aliasToTable, allTables);
        }
    }

    /**
     * The names this echo prints BARE. An expression in the ORDER BY clause is re-printed as it
     * resolves in the query's OUTPUT scope — a projected column is the column itself and has no
     * relation to qualify it with — while the same expression anywhere earlier resolves against the
     * FROM and is qualified. Both are live's behaviour, measured side by side:
     *
     * <pre>
     *   … GROUP BY a ORDER BY ROW_NUMBER() OVER (ORDER BY a)   [… OVER (ORDER BY A ASC NULLS LAST)]
     *   … GROUP BY a HAVING ROW_NUMBER() OVER (ORDER BY a) = 1 [… OVER (ORDER BY GW.A ASC NULLS LAST)]
     * </pre>
     *
     * <p>A key the output does NOT carry falls back to the qualified form in the same echo, which is
     * what makes {@code ORDER BY a, b} print as {@code A ASC NULLS LAST, GW.B ASC NULLS LAST}. A
     * qualifier the user WROTE always survives, output column or not.
     *
     * @param names the output column names, upper-cased
     */
    void printInOutputScope(final Set<String> names) {
        printer.setOutputScopeNames(names);
    }

    /**
     * Have this echo spell a name read through the row around the query as the plan holds it: an aggregate a
     * subquery writes in its WHERE over an enclosing column is {@code [MAX(CORRELATION(FZ.ID) + G.V)]}
     * (live-verified).
     *
     * @param qualifiers the enclosing row's qualifiers, upper-cased
     */
    void readEnclosingQualifiers(final Set<String> qualifiers) {
        printer.setEnclosingQualifiers(qualifiers);
    }

    /** Any expression as live re-prints it, or its source text when it will not re-parse. */
    String print(final ParserRuleContext expression) {
        final String written = ParseTreeText.getOriginalText(expression);
        final String qualified = qualified(written);
        return qualified != null ? qualified : written;
    }

    /**
     * An expression as live's PLAN holds it, the rewrites the plan applies included: a cast written with
     * {@code ::} is {@code CAST(0.5 AS FLOAT)}, and a subquery is the query the plan runs. A text that will not
     * re-parse prints as written.
     *
     * @param expression the expression
     * @return its plan text
     */
    String printPlanned(final ParserRuleContext expression) {
        final String written = ParseTreeText.getOriginalText(expression);
        try {
            return printer.plannedText(ExpressionEvaluator.parse(written));
        } catch (final RuntimeException notAnExpression) {
            return written;
        }
    }

    /**
     * An aggregate call a sentence names by itself, as live's PLAN computes it: {@link #printPlanned}, and a call
     * the plan computes in two steps named by both, {@code HLL_ESTIMATE(HLL_ACCUMULATE(FZ.ID))} for a distinct
     * count. A text that will not re-parse prints as written.
     *
     * @param call the call
     * @return its plan text
     */
    String printPlannedCall(final ParserRuleContext call) {
        final String written = ParseTreeText.getOriginalText(call);
        try {
            return printer.plannedCallDefinition(ExpressionEvaluator.parse(written));
        } catch (final RuntimeException notAnExpression) {
            return written;
        }
    }

    /**
     * Refuses a call whose arguments are of a family it does not take, from their declared types and at the call's
     * own place: {@code Invalid argument types for function 'SUM': (BOOLEAN)} for an AVG over a BOOLEAN. A call that
     * will not re-parse, and a fault that is no compilation error, are left to whoever computes the call.
     *
     * @param call the call
     */
    void refuseArgumentFamilies(final ParserRuleContext call) {
        refuseArgumentTypes(call, false);
    }

    /**
     * {@link #refuseArgumentFamilies}, and with {@code strict} the call's every argument type and its own type as
     * well: an aggregate is typed whole before the place it stands in is judged — {@code VECTOR_AVG} over a VARIANT,
     * {@code BITAND_AGG} over a BOOLEAN, {@code BOOLAND} over BOOLEANs and an AVG whose intermediate type no NUMBER
     * holds are refused for the type (live-verified).
     *
     * @param call   the call
     * @param strict whether the arguments' types and the call's own are judged too
     */
    void refuseArgumentTypes(final ParserRuleContext call, final boolean strict) {
        final Expression parsed;
        try {
            parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(call));
        } catch (final RuntimeException notAnExpression) {
            return;
        }
        final SourcePosition displaced = ExpressionSource.beginNested(
            new SourcePosition(call.getStart().getLine(), call.getStart().getCharPositionInLine()));
        try {
            printer.rejectArgumentFamilies(parsed);
            if (strict) {
                printer.validateStrict(parsed);
                printer.inferStaticType(parsed);
            }
        } catch (final RuntimeException refused) {
            if (SqlCompilationError.isCompilationError(refused.getMessage())
                    || refused instanceof UnsupportedFeatureException) {
                throw refused;
            }
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /**
     * One of the two calls a NESTING message brackets, as live's PLAN holds it. The shared strict
     * re-print already spells the rewritten aggregates the way the plan does — MEDIAN as the 0.5
     * percentile with its CAST, a plain AVG as its SUM-over-COUNT definition — and this adds the one
     * rule that is the BRACKET's rather than the expression's: an AVG that is itself the bracketed
     * call is named by its SUM half, {@code [SUM(T.N)] nested in [SUM(SUM(T.N))]} for
     * {@code AVG(SUM(n))} and {@code [SUM(T.N)] nested in [SUM((CAST(SUM(T.N) AS NUMBER(28,8))) /
     * (COUNT(T.N)))]} for {@code SUM(AVG(n))}. A call that will not re-parse prints as written.
     */
    /**
     * Types the select aliases a printed key may read, so an operation over one prints as the plan holds it:
     * {@code MAX(x / 0)} over an alias of a NUMBER(38,0) is {@code MAX(SCALED_ROUND_INT_DIVIDE(X, 0))}.
     *
     * @param types each alias's type, keyed upper-cased
     */
    void typeAliases(final Map<String, DataType> types) {
        printer.setOutputAliasTypes(types);
    }

    String printAggregateCall(final ParserRuleContext call) {
        final String written = ParseTreeText.getOriginalText(call);
        try {
            return printer.qualifiedAggregateText(ExpressionEvaluator.parse(written));
        } catch (final RuntimeException notAnExpression) {
            return written;
        }
    }

    /** An expression's declared type as live names it, or null when it does not resolve. */
    private String typeText(final Expression expression) {
        try {
            return printer.argumentTypeText(expression);
        } catch (final RuntimeException notTypeable) {
            return null;
        }
    }

    /**
     * A call whose WINDOW SPECIFICATION is missing, echoed as live's plan holds it. For most functions
     * that is the ordinary re-print; for the two that take a PREDICATE it also shows the conversion
     * they apply, because live's plan carries the argument already cast:
     *
     * <pre>
     *   CONDITIONAL_TRUE_EVENT(t)              CONDITIONAL_TRUE_EVENT(CAST(AW.T AS BOOLEAN))
     *   CONDITIONAL_TRUE_EVENT(i &gt; 1)          CONDITIONAL_TRUE_EVENT(CAST(AW.I &gt; 1 AS BOOLEAN))
     *   CONDITIONAL_TRUE_EVENT(b IS NULL)      CONDITIONAL_TRUE_EVENT(CAST(AW.B IS NULL AS BOOLEAN))
     *   CONDITIONAL_TRUE_EVENT(STARTSWITH(t,'1'))
     *                                          CONDITIONAL_TRUE_EVENT(CAST(STARTSWITH(AW.T,'1')
     *                                          AS BOOLEAN))
     * </pre>
     *
     * <p>★ WHAT ESCAPES THE CAST IS NOT "already a boolean" — a comparison and an IS NULL are booleans
     * too, and both are cast. It is the LOGICAL vocabulary that escapes: a BOOLEAN column, a boolean
     * literal, and NOT / AND / OR. Live-verified across all of {@code b}, {@code (b)}, {@code aw.b},
     * {@code NOT b}, {@code b AND b}, {@code b OR FALSE} and {@code TRUE}, none of which are cast,
     * against the four above, all of which are.
     *
     * <p>★ THE TWO EVENT FUNCTIONS DIFFER on a NON-boolean argument, and
     * {@link BooleanArgumentWindowFunctions} holds the measured table: CONDITIONAL_TRUE_EVENT counts a
     * predicate and converts, CONDITIONAL_CHANGE_EVENT counts a value and does not.
     *
     * @param call the call
     * @return its plan text
     */
    String printSpecificationlessCall(final FrostlakeParser.FunctionCallExprContext call) {
        final String name = SqlIdentifiers.canonicalText(call.functionName().getText()).toUpperCase(Locale.ROOT);
        final List<FrostlakeParser.BooleanExprContext> args =
            ParseTreeText.functionBooleanArgs(call.functionArgList());
        if (!BooleanArgumentWindowFunctions.spellsOutABooleanArgument(name) || args.size() != 1) {
            return print(call);
        }
        final String written = ParseTreeText.getOriginalText(args.get(0)).trim();
        final Expression argument;
        try {
            argument = ExpressionEvaluator.parse(written);
        } catch (final RuntimeException notAnExpression) {
            return print(call);
        }
        if (isLogical(argument)) {
            return print(call);
        }
        final boolean converted = BooleanArgumentWindowFunctions.coercesArgumentToBoolean(name);
        if (!converted && !isSpelledOutPredicate(argument)) {
            return print(call);
        }
        final String plan = qualified(written);
        return plan == null ? print(call)
            : name + "(CAST(" + unwrapped(plan) + " AS BOOLEAN))";
    }

    /**
     * Whether an expression belongs to the LOGICAL vocabulary, which live leaves uncast in a predicate
     * position: a BOOLEAN column, a boolean literal, or NOT / AND / OR.
     */
    private boolean isLogical(final Expression expression) {
        if (expression instanceof BinaryOperationExpression) {
            // An AND / OR is logical only when BOTH sides are: live casts (T.N > 1) AND (T.I < 2) as a
            // whole, and leaves T.B AND T.B alone.
            final BinaryOperationExpression binary = (BinaryOperationExpression) expression;
            return (binary.getOperator() == BinaryOperator.AND || binary.getOperator() == BinaryOperator.OR)
                && isLogical(binary.getLeft()) && isLogical(binary.getRight());
        }
        if (expression instanceof UnaryOperationExpression) {
            // NOT over a boolean is logical (NOT(T.B) prints bare); NOT over a comparison or an IS NULL
            // is cast around the NOT: CAST(NOT(T.N > (CAST(1 AS NUMBER(10,2)))) AS BOOLEAN).
            final UnaryOperationExpression unary = (UnaryOperationExpression) expression;
            return unary.getOperator() == UnaryOperator.NOT && isLogical(unary.getOperand());
        }
        if (expression instanceof LiteralExpression) {
            return ((LiteralExpression) expression).getType() == LiteralType.BOOLEAN;
        }
        if (expression instanceof ColumnReferenceExpression) {
            return "BOOLEAN".equals(typeText(expression));
        }
        return false;
    }

    /**
     * Whether an expression is a PREDICATE the plan spells out — a comparison, an IS NULL, or a call
     * that returns a boolean. All three are booleans already and live casts them anyway, which is what
     * separates this from the logical vocabulary above.
     *
     * <p>Read from the NODE, not from the declared type: this engine does not type a comparison or a
     * NOT as BOOLEAN, so a type test would put them both on the wrong side.
     */
    private boolean isSpelledOutPredicate(final Expression expression) {
        if (expression instanceof IsNullExpression) {
            return true;
        }
        if (expression instanceof BinaryOperationExpression) {
            return isComparison(((BinaryOperationExpression) expression).getOperator());
        }
        return expression instanceof FunctionCallExpression
            && "BOOLEAN".equals(typeText(expression));
    }

    /** The operators that compare rather than combine. LIKE and its family are predicates too. */
    private static boolean isComparison(final BinaryOperator operator) {
        switch (operator) {
            case EQUAL:
            case NOT_EQUAL:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL:
            case LIKE:
            case ILIKE:
            case NOT_LIKE:
            case NOT_ILIKE:
                return true;
            default:
                return false;
        }
    }

    /**
     * A printed expression with one FULLY enclosing bracket pair removed. The shared printer brackets
     * some nodes unconditionally — an IS NULL comes back as {@code (X IS NULL)} — and live prints the
     * cast around the bare form. Only a pair that encloses the WHOLE text is removed, so the leading
     * bracket of something like {@code (a + 1) > 2} stays where it is.
     */
    private static String unwrapped(final String text) {
        if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') {
            return text;
        }
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '(') {
                depth++;
            } else if (text.charAt(i) == ')') {
                depth--;
                if (depth == 0 && i < text.length() - 1) {
                    return text;
                }
            }
        }
        return depth == 0 ? text.substring(1, text.length() - 1) : text;
    }

    /**
     * A window call WITHOUT its specification — the shape an ARITY refusal echoes. Live prints
     * {@code RANK(GW.A)} for a mis-counted {@code RANK(a) OVER (ORDER BY a)}: the arguments are the
     * complaint, so the window they were going to be computed over is left out entirely.
     *
     * @param call the call
     * @return its name and arguments, qualified as the plan holds them
     */
    String printBareCall(final FrostlakeParser.FunctionCallExprContext call) {
        // With no OVER there is nothing to trim, and the call prints as any other expression does.
        if (call.overClause() == null) {
            return print(call);
        }
        final String written = bareCallText(call);
        if (written == null) {
            return print(call);
        }
        final String plan = qualified(written);
        return plan != null ? plan : written;
    }

    /** A WINDOW call as live re-prints it, OVER clause and all. */
    String printWindowCall(final FrostlakeParser.FunctionCallExprContext call) {
        final String bare = qualified(bareCallText(call));
        if (bare == null || call.overClause() == null) {
            return ParseTreeText.getOriginalText(call);
        }
        final StringBuilder text = new StringBuilder(bare).append(" OVER (");
        final FrostlakeParser.OverClauseContext over = call.overClause();
        if (over.partitionByClause() != null) {
            text.append("PARTITION BY ");
            appendPartitionKeys(text, over.partitionByClause());
            if (over.orderByClause() != null) {
                text.append(' ');
            }
        }
        if (over.orderByClause() != null) {
            text.append("ORDER BY ");
            appendSortKeys(text, over.orderByClause().orderItem());
        }
        return text.append(')').toString();
    }

    private void appendPartitionKeys(final StringBuilder text,
                                     final FrostlakeParser.PartitionByClauseContext partition) {
        final List<ParserRuleContext> keys = KeyContexts.partitionKeys(partition);
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(print(keys.get(i)));
        }
    }

    private void appendSortKeys(final StringBuilder text,
                                final List<FrostlakeParser.OrderItemContext> items) {
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            final FrostlakeParser.OrderItemContext item = items.get(i);
            final boolean ascending = item.DESC() == null;
            final Boolean nullsFirst = ValueComparisons.nullsFirstFlag(item);
            text.append(print(KeyContexts.of(item)))
                .append(ascending ? " ASC NULLS " : " DESC NULLS ")
                .append(SessionNullOrdering.nullsFirst(ascending, nullsFirst) ? "FIRST" : "LAST");
        }
    }

    /** {@code text} re-printed with every bare column qualified, or null when it will not re-parse. */
    private String qualified(final String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return printer.qualifiedText(ExpressionEvaluator.parse(text));
        } catch (final RuntimeException notAnExpression) {
            return null;
        }
    }

    /**
     * The call WITHOUT its OVER clause, read from the source between the call's first character and
     * the OVER keyword. A trailing {@code IGNORE NULLS} sits inside that span and is dropped by the
     * re-print, which is what live does with it.
     */
    private static String bareCallText(final FrostlakeParser.FunctionCallExprContext call) {
        final Token start = call.getStart();
        if (call.overClause() == null || start.getInputStream() == null) {
            return null;
        }
        final int last = call.overClause().getStart().getStartIndex() - 1;
        if (last < start.getStartIndex()) {
            return null;
        }
        return start.getInputStream().getText(new Interval(start.getStartIndex(), last)).trim();
    }
}
