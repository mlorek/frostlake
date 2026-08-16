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

import dev.frostlake.executor.expressions.ArrayAccessExpression;
import dev.frostlake.executor.expressions.AstPrinterVisitor;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.CollatedKey;
import dev.frostlake.executor.expressions.CollationSpec;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.FoldedValues;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.ObjectAccessExpression;
import dev.frostlake.executor.expressions.SortKeyRole;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.functions.AggregateFunction;
import dev.frostlake.functions.MultiArgumentAccumulator;
import dev.frostlake.functions.aggregate.ApproxPercentileAccumulator;
import dev.frostlake.functions.aggregate.ApproximateAwareAccumulator;
import dev.frostlake.functions.aggregate.CoercedNumericArgumentAccumulator;
import dev.frostlake.functions.aggregate.ConstantArgumentsAccumulator;
import dev.frostlake.functions.aggregate.CorrAccumulator;
import dev.frostlake.functions.aggregate.CovarAccumulator;
import dev.frostlake.functions.aggregate.DeclaredArgumentAccumulator;
import dev.frostlake.functions.aggregate.ListAggAccumulator;
import dev.frostlake.functions.aggregate.MaxByMinByAccumulator;
import dev.frostlake.functions.aggregate.ObjectAggAccumulator;
import dev.frostlake.functions.aggregate.PercentileContAccumulator;
import dev.frostlake.functions.aggregate.PercentileDiscAccumulator;
import dev.frostlake.functions.aggregate.RegrAccumulator;
import dev.frostlake.functions.window.WholePartitionAggregates;
import dev.frostlake.functions.window.WindowFunctionHelper;
import dev.frostlake.functions.window.WindowFunctionNames;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.VariantType;
import dev.frostlake.values.VariantJsonNulls;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Window-function query stage extracted from {@link QueryExecutor}: OVER-clause partitioning and
 * ordering, the ranking / navigation / value / aggregate window functions, window frames, and the
 * aggregate/window detection predicates. Pure parse-tree/value work is delegated to the stateless
 * {@link ParseTreeText} and {@link ValueComparisons} helpers; the few callbacks that need engine
 * state (expression evaluation, the function registry) are reached through the owning executor.
 */
final class WindowFunctionEvaluator {

    private static final Logger logger = LoggerFactory.getLogger(WindowFunctionEvaluator.class);

    private final QueryExecutor executor;

    // SELECT-list alias -> its defining expression text, for the window computation currently running. A
    /** The window functions that take no arguments — a call with any refuses (live-verified). */
    private static final java.util.Set<String> ZERO_ARGUMENT_WINDOW_FUNCTIONS = java.util.Set.of(
        "ROW_NUMBER", "RANK", "DENSE_RANK", "CUME_DIST", "PERCENT_RANK");

    // window PARTITION BY / ORDER BY may reference a SELECT alias (e.g. QUALIFY ROW_NUMBER() OVER
    // (PARTITION BY <alias> ...)); when such a key isn't a base column it resolves to this expression.
    // Set/restored around computeWindowFunctions so a nested subquery's window functions don't clobber it;
    // empty outside a window computation, so evaluateOrderKey behaves exactly as before there.
    private Map<String, String> windowSelectAliases = new HashMap<>();

    // Canonical AST print of each SELECT item -> projected column index, for the window computation
    // currently running over ALREADY-PROJECTED (grouped) rows; empty otherwise. Lets a PARTITION BY /
    // ORDER BY key that IS a select item — OVER (ORDER BY SUM(amount) DESC) — read the computed value.
    private Map<String, Integer> windowSelectItemCanonicalIndex = new HashMap<>();
    private final Deque<Map<String, Integer>> savedCanonicalScopes = new ArrayDeque<>();

    // How to compute an expression over a grouped output row's SOURCE GROUP, for the window computation
    // currently running over already-projected rows; null otherwise. A key or an argument that is a raw
    // aggregate — OVER (ORDER BY SUM(b)), LAG(SUM(b)) — has no value on the projected row unless the
    // SELECT list happens to carry it, and the group is the only place left to compute it. Saved and
    // restored around the computation so a nested subquery cannot clobber it.
    private GroupedExpressionValues groupedValues;

    WindowFunctionEvaluator(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Collect window-function calls ({@code fn(...) OVER (...)}) anywhere in a parse subtree. Does not
     * descend into a window call's own OVER spec — those expressions belong to the window definition,
     * not the surrounding predicate.
     */
    void collectWindowFunctionCalls(final ParseTree node,
                                            final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (isSystemTypeofCall(node)) {
            // A window call inside SYSTEM$TYPEOF is typed, never computed (see typeofAggregatesFoldToScan).
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null) {
            out.add((FrostlakeParser.FunctionCallExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectWindowFunctionCalls(node.getChild(i), out);
        }
    }

    /**
     * Positional argument contexts of a window-function call. The function-argument grammar is a
     * {@code booleanExpr} list (a superset of {@code expression}), so a window function's args — always
     * columns / numbers — are read from here; only their original source text is used.
     */
    private static List<FrostlakeParser.BooleanExprContext> windowArgs(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        return ParseTreeText.functionBooleanArgs(funcCtx.functionArgList());
    }

    /**
     * The window calls written INSIDE {@code SYSTEM$TYPEOF} calls under {@code node} — typed rather than
     * computed, but their arguments are still judged at plan time, so the caller puts them through
     * {@link #rejectFileWindowArguments}: {@code SYSTEM$TYPEOF(RATIO_TO_REPORT(d) OVER ())} over a
     * DATE is "Invalid argument types for function 'SUM': (DATE)" on the account.
     */
    void collectTypeofWindowFunctionCalls(final ParseTree node,
                                          final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (node == null) {
            return;
        }
        if (isSystemTypeofCall(node)) {
            collectWindowFunctionCallsEverywhere(node, out);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectTypeofWindowFunctionCalls(node.getChild(i), out);
        }
    }

    private void collectWindowFunctionCallsEverywhere(final ParseTree node,
                                                      final List<FrostlakeParser.FunctionCallExprContext> out) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext
                && ((FrostlakeParser.FunctionCallExprContext) node).overClause() != null) {
            out.add((FrostlakeParser.FunctionCallExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectWindowFunctionCallsEverywhere(node.getChild(i), out);
        }
    }

    /** Whether a parse node is a {@code SYSTEM$TYPEOF} call, whose argument is typed and never evaluated. */
    static boolean isSystemTypeofCall(final ParseTree node) {
        return node instanceof FrostlakeParser.SystemFuncExprContext
            && "SYSTEM$TYPEOF".equalsIgnoreCase(
                ((FrostlakeParser.SystemFuncExprContext) node).SYSTEM_FUNC().getText());
    }

    /**
     * Whether a select list whose aggregates ALL sit inside {@code SYSTEM$TYPEOF} calls runs as a plain
     * scan on the account, one row per input row, rather than as an aggregate query. The typeof is a
     * compile-time constant there, so the aggregate it wraps is never computed; what decides the
     * shape is whether the account could have answered that aggregate WITHOUT scanning — from a
     * partition's statistics or a constant — in which case the aggregate stays in the plan and the
     * query answers ONE row (live-verified over a two-row table): {@code COUNT(*)}, {@code COUNT(c)},
     * {@code COUNT(1)}, {@code MIN}/{@code MAX} over a NUMBER, DATE, TIMESTAMP or BOOLEAN column, over
     * a literal or over arithmetic on those all answer one row, while {@code SUM}, {@code AVG},
     * {@code MEDIAN}, the deviations, {@code ANY_VALUE}, {@code LISTAGG}, {@code ARRAY_AGG},
     * {@code COUNT(DISTINCT …)}, a COUNT over a computed value ({@code COUNT(c + 1)}, a derived relation's
     * computed column), {@code COUNT_IF}, {@code MIN}/{@code MAX} over a VARCHAR, a FLOAT or
     * a computed value ({@code MAX(LENGTH(k))}) answer one row per input row. So do COUNT, MIN and MAX
     * once the query reads past its table's statistics: a join, or a WHERE the statistics cannot prove
     * (see {@link CountStatisticsBound}). A grouped query, a HAVING and any aggregate written outside a
     * typeof keep the aggregate shape whatever sits inside.
     *
     * @param ctx   the select clause
     * @param table the FROM relation, which types the arguments
     * @return true when every aggregate lives inside a typeof and at least one of them is of the
     *         family the account folds away with the call
     */
    boolean typeofAggregatesFoldToScan(final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        final boolean[] verdict = typeofAggregateVerdict(ctx, table);
        return verdict[0] && !verdict[1];
    }

    /**
     * Whether a select list pairs a typeof over a folded-away aggregate with aggregates OUTSIDE any typeof
     * that the account answers from statistics: {@code SELECT SYSTEM$TYPEOF(SUM(c)), COUNT(*) FROM t}. The
     * account then keeps the scan the typeof's aggregate left behind and answers one row per input row,
     * each carrying the statistic over the whole input, and still one row over no input at all
     * (live-verified). An outside aggregate the account really computes ({@code MAX} over a VARCHAR,
     * {@code COUNT(DISTINCT …)}) keeps the ordinary single row.
     *
     * @param ctx   the select clause
     * @param table the FROM relation, which types the arguments
     * @return true when the one aggregate row stands for every input row
     */
    boolean typeofBesideStatisticsReplicates(final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        final boolean[] verdict = typeofAggregateVerdict(ctx, table);
        return verdict[0] && verdict[1] && !verdict[2];
    }

    /**
     * The select list's aggregates classified: [0] a folded-away aggregate inside a typeof, [1] any
     * aggregate outside one, [2] an outside aggregate the account cannot answer from statistics.
     */
    private boolean[] typeofAggregateVerdict(final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        final boolean[] verdict = new boolean[3];
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            classifyTypeofAggregates(SelectItemAccessors.getItemValueExpr(item), false, table, verdict);
        }
        return verdict;
    }

    private void classifyTypeofAggregates(final ParseTree node, final boolean insideTypeof,
                                          final Table table, final boolean[] verdict) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.OverClauseContext) {
            return;
        }
        if (isSystemTypeofCall(node)) {
            for (int i = 0; i < node.getChildCount(); i++) {
                classifyTypeofAggregates(node.getChild(i), true, table, verdict);
            }
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) node;
            if (funcCtx.overClause() == null && executor.getFunctionRegistry().hasAggregateFunction(
                    aggregateLookupKey(funcCtx.functionName().getText()))) {
                if (insideTypeof) {
                    verdict[0] |= !isStatisticsAnswerable(funcCtx, table);
                } else {
                    verdict[1] = true;
                    verdict[2] |= !isStatisticsAnswerable(funcCtx, table);
                }
                return;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext funcCtx = (FrostlakeParser.FunctionCallStarExprContext) node;
            if (executor.getFunctionRegistry().hasAggregateFunction(
                    aggregateLookupKey(funcCtx.functionName().getText()))) {
                // COUNT(*) is answered from statistics while the query reads within them.
                if (insideTypeof) {
                    verdict[0] |= executor.countIsUnbounded();
                } else {
                    verdict[1] = true;
                    verdict[2] |= executor.countIsUnbounded();
                }
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            classifyTypeofAggregates(node.getChild(i), insideTypeof, table, verdict);
        }
    }

    /**
     * Whether an aggregate call is one the account answers from statistics or a constant: never once the
     * query reads past its table's statistics ({@link QueryExecutor#countIsUnbounded}).
     */
    private boolean isStatisticsAnswerable(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                           final Table table) {
        final String name = aggregateLookupKey(funcCtx.functionName().getText()).toUpperCase(Locale.ROOT);
        if (name.equals("COUNT")) {
            // Over a stored column or a constant only: COUNT(c + 1) scans on the account.
            final List<FrostlakeParser.BooleanExprContext> counted = windowArgs(funcCtx);
            if (counted.size() != 1 || carriesDistinct(funcCtx) || executor.countIsUnbounded()) {
                return false;
            }
            try {
                return evaluatorOver(table).countsStoredColumn(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(counted.get(0))));
            } catch (final RuntimeException undetermined) {
                return false;
            }
        }
        if (!name.equals("MIN") && !name.equals("MAX")) {
            return false;
        }
        final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);
        if (args.size() != 1 || carriesDistinct(funcCtx) || executor.countIsUnbounded()) {
            return false;
        }
        try {
            return isStatisticsShaped(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(args.get(0))),
                evaluatorOver(table));
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /** A literal, a NUMBER / DATE / TIMESTAMP / BOOLEAN column, or +, - and * over those. */
    static boolean isStatisticsShaped(final Expression argument, final ExpressionEvaluator types) {
        if (argument instanceof LiteralExpression) {
            return ((LiteralExpression) argument).getType() != LiteralType.NULL;
        }
        if (argument instanceof ColumnReferenceExpression) {
            final DataType declared = types.inferStaticType(argument);
            return declared instanceof NumericType && !NumericType.isApproximate(declared)
                || declared instanceof DateTimeType || declared instanceof BooleanType;
        }
        if (argument instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) argument;
            final BinaryOperator op = binary.getOperator();
            return (op == BinaryOperator.ADD || op == BinaryOperator.SUBTRACT || op == BinaryOperator.MULTIPLY)
                && isStatisticsShaped(binary.getLeft(), types) && isStatisticsShaped(binary.getRight(), types);
        }
        return false;
    }

    /** Whether a call spells DISTINCT before its argument, at its own level only. */
    private static boolean carriesDistinct(final ParseTree call) {
        for (int i = 0; i < call.getChildCount(); i++) {
            final ParseTree child = call.getChild(i);
            if (child instanceof TerminalNode) {
                if (((TerminalNode) child).getSymbol().getType() == FrostlakeParser.DISTINCT) {
                    return true;
                }
            } else if (!(child instanceof FrostlakeParser.FunctionCallExprContext)
                    && !(child instanceof FrostlakeParser.SelectStatementContext)
                    && carriesDistinct(child)) {
                return true;
            }
        }
        return false;
    }

    boolean isSimpleStar(final FrostlakeParser.SelectClauseContext ctx) {
        final List<FrostlakeParser.SelectItemContext> items = ctx.selectList().selectItem();
        // A star carrying EXCLUDE/RENAME/REPLACE/ILIKE modifiers is NOT a pass-through: it must go through
        // projection so those modifiers reshape the row values, not just the column metadata.
        return items.size() == 1 && SelectItemAccessors.isStarItem(items.get(0))
            && SelectItemAccessors.getStarModifiers(items.get(0)).isEmpty();
    }

    boolean hasAggregateFunction(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) continue;
            if (hasAggregateFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a whole CLAUSE carries an aggregate of this query's — an ORDER BY key or a QUALIFY
     * predicate, either of which makes the query aggregate exactly as a select item would.
     *
     * @param node the clause's parse tree
     * @return true when it contains an aggregate call belonging to this query
     */
    boolean hasAggregateInTree(final ParseTree node) {
        return containsAggregate(node);
    }

    boolean hasAggregateFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        // A null value expression means the select item is a boolean (AND/OR/NOT) projection,
        // which has no single value expression and is never itself an aggregate.
        if (expr == null) {
            return false;
        }
        return containsAggregate(expr);
    }

    /**
     * Whether {@code node}'s tree contains a call to an aggregate function belonging to THIS query. It
     * recurses through EVERY child — operators, CASE branches, and crucially function-call ARGUMENTS (the
     * {@code MIN} in {@code NVL(MIN(x), 0)}, which is nested under functionArgList/functionArg/booleanExpr,
     * not a direct expression child). Two boundaries are pruned: a nested subquery ({@code selectStatement}
     * — its aggregates are the subquery's, not this query's) and a window {@code OVER} clause (a windowed
     * call is not an aggregate, and its PARTITION / ORDER keys are not this query's aggregates).
     */
    /**
     * The window functions that REQUIRE an ORDER BY in their window specification. Live refuses all
     * eleven with "Window function type [ROW_NUMBER] requires ORDER BY in window specification.",
     * naming the function, and it refuses the PARTITION-only spelling too — {@code OVER (PARTITION BY a)}
     * is no more acceptable than a bare {@code OVER ()}, which is the shape a user is likeliest to
     * write by mistake.
     *
     * <p>The aggregates used as windows are NOT here and must not be: {@code SUM(a) OVER ()},
     * {@code COUNT(*) OVER ()}, MIN, MAX and AVG all answer with no ORDER BY at all.
     */
    private static final Set<String> ORDER_BY_REQUIRED = new HashSet<>(Arrays.asList(
        "ROW_NUMBER", "RANK", "DENSE_RANK", "PERCENT_RANK", "CUME_DIST", "NTILE",
        "LAG", "LEAD", "FIRST_VALUE", "LAST_VALUE", "NTH_VALUE"));

    /**
     * Refuses a window call from that family whose specification carries no ORDER BY, at PLAN time.
     * The value functions already refused while computing their frame, which never fires over ZERO
     * ROWS — an empty table accepted the statement and a view over it would have been created.
     *
     * @param node the tree to walk
     */
    /**
     * Refuses a window FRAME written with no ORDER BY beside it, which live rejects outright:
     * {@code AVG(a) OVER (ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING)} is
     * "Window frame requires an ORDER BY clause." A PARTITION BY does not satisfy it — only an ORDER BY
     * does — and every frame spelling offends equally: ROWS and RANGE, BETWEEN and the bare
     * {@code ROWS UNBOUNDED PRECEDING} / {@code ROWS 2 PRECEDING} / {@code ROWS CURRENT ROW} forms.
     *
     * <p>THE RULE IS ABOUT THE FRAME, NOT THE FUNCTION. AVG, SUM, COUNT(*), MIN, ARRAY_AGG and the
     * ranking family are all refused the same way, and a frame BESIDE an ORDER BY is legal on all of
     * them, ranking functions included.
     *
     * <p>IT OUTRANKS EVERYTHING BUT A SYNTAX ERROR, which is why it is raised before the relation is
     * even resolved. Measured against each of its neighbours, with the other problem written first:
     * it beats the missing-ORDER-BY sentence for the ranking family, an invalid identifier in the
     * PARTITION BY key, an unknown function name, an ungrouped select item, and even
     * "Object … does not exist". Nothing needs to be looked up to see it, and live evidently looks at
     * nothing.
     *
     * <p>The position is the OVER keyword's own — not the frame's, though the frame is what offends —
     * verified across eleven offsets including a nested call, a QUALIFY, an ORDER BY, a subquery's
     * inner window and a second line.
     *
     * @param node the tree to walk
     */
    void rejectFrameWithoutOrderBy(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.OverClauseContext) {
            final FrostlakeParser.OverClauseContext over = (FrostlakeParser.OverClauseContext) node;
            if (over.windowFrame() != null && over.orderByClause() == null) {
                throw new RuntimeException(SqlCompilationError.at(over.getStart().getLine(),
                    over.getStart().getCharPositionInLine(),
                    "Window frame requires an ORDER BY clause."));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectFrameWithoutOrderBy(node.getChild(i));
        }
    }

    /**
     * A window frame that shows a {@link WholePartitionAggregates whole-partition aggregate} less than
     * its whole partition. Live refuses it at compile time — the WITHIN GROUP clause, or the ranking
     * these functions do internally, already orders the values, and a moving frame would ask for a
     * second, incompatible ordering. Which of the two sentences it gets is
     * {@link WindowFrameShape#kindWord the frame's shape}:
     *
     * <pre>
     *   MEDIAN(n) OVER (ORDER BY n)                             Cumulative … for function MEDIAN
     *   MEDIAN(n) OVER (ORDER BY n ROWS UNBOUNDED PRECEDING)    Cumulative … for function MEDIAN
     *   MEDIAN(n) OVER (ORDER BY n ROWS 1 PRECEDING)            Sliding    … for function MEDIAN
     * </pre>
     *
     * <p>The anchor is the FRAME where one is written and the OVER keyword otherwise, which is the same
     * rule live's other frame refusals follow: it points at the narrowest clause that is wrong. Both
     * outrank the relation — a statement whose FROM names nothing still gets this sentence — which is
     * why the walk runs before anything is resolved.
     *
     * <p>Two shapes are LEFT ALONE here. A specification with no ORDER BY and no frame is legal and
     * answers over the partition; so is a ROWS frame spanning the whole partition. The RANGE spelling
     * of that same whole-partition frame is not, but its refusal needs a resolved plan to echo and is
     * raised later.
     *
     * @param node any parse-tree node; the walk covers the statement
     */
    void rejectUnsupportedFrameForWholePartitionAggregate(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call =
                (FrostlakeParser.FunctionCallExprContext) node;
            final String name = aggregateLookupKey(call.functionName().getText());
            final FrostlakeParser.OverClauseContext over = call.overClause();
            if (over != null && WholePartitionAggregates.coversWholePartitionOnly(name)
                    && (over.orderByClause() != null || over.windowFrame() != null)
                    && !WindowFrameShape.spansWholePartition(over.windowFrame())) {
                final ParserRuleContext anchor = over.windowFrame() != null
                    ? over.windowFrame() : over;
                throw new RuntimeException(SqlCompilationError.at(anchor.getStart().getLine(),
                    anchor.getStart().getCharPositionInLine(),
                    WindowFrameShape.kindWord(over.windowFrame())
                        + " window frame unsupported for function " + name));
            }
            // ★ DISTINCT CANNOT BE ORDERED OR FRAMED — except under COUNT, which answers every frame,
            // and except the whole-partition ROWS frame, which re-states the partition (both
            // live-verified). Refused at the OVER keyword.
            if (over != null && call.DISTINCT() != null && !"COUNT".equals(name)
                    && (over.orderByClause() != null || over.windowFrame() != null)
                    && !(over.windowFrame() != null && !WindowFrameShape.isRange(over.windowFrame())
                        && WindowFrameShape.spansWholePartition(over.windowFrame()))) {
                throw new RuntimeException(SqlCompilationError.at(over.getStart().getLine(),
                    over.getStart().getCharPositionInLine(),
                    "distinct cannot be used with a window frame or an order."));
            }
            // ★ A WITHIN GROUP ORDERING CANNOT MEET AN OVER ORDERING: ARRAY_AGG(n) WITHIN GROUP (ORDER
            // BY n) OVER (ORDER BY n) is refused at the OVER keyword, with a one-edge, a two-edge or the
            // whole-partition RANGE frame alike; the same call over a PARTITION alone, or over the
            // whole-partition ROWS frame, answers. A whole-partition family member is judged in its own
            // words instead, whatever it orders WITHIN GROUP.
            if (over != null && call.withinGroupClause() != null && over.orderByClause() != null
                    && !WholePartitionAggregates.coversWholePartitionOnly(name)
                    && !(over.windowFrame() != null && !WindowFrameShape.isRange(over.windowFrame())
                        && WindowFrameShape.spansWholePartition(over.windowFrame()))) {
                throw new RuntimeException(SqlCompilationError.at(over.getStart().getLine(),
                    over.getStart().getCharPositionInLine(),
                    "WITHIN GROUP clause is not supported when the OVER clause contains an ORDER BY clause."));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectUnsupportedFrameForWholePartitionAggregate(node.getChild(i));
        }
    }

    void rejectWindowWithoutRequiredOrderBy(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext call =
                (FrostlakeParser.FunctionCallExprContext) node;
            if (call.overClause() != null && call.overClause().orderByClause() == null
                    && ORDER_BY_REQUIRED.contains(aggregateLookupKey(call.functionName().getText()))) {
                throw new RuntimeException(SqlCompilationError.of("Window function type ["
                    + aggregateLookupKey(call.functionName().getText())
                    + "] requires ORDER BY in window specification."));
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            rejectWindowWithoutRequiredOrderBy(node.getChild(i));
        }
    }

    /**
     * The name an aggregate is looked up by. The call's TEXT is not that name: a quoted spelling
     * carries its quotes, so upper-casing it whole asked the registry for {@code "SUM"} — quotes
     * included — which matches nothing, and {@code "sum"(a)} was refused as an unknown function while
     * the scalar {@code "abs"(a)} beside it worked. A quoted function name resolves case-INSENSITIVELY
     * on a real account: {@code "SUM"(a)}, {@code "sum"(a)} and {@code sum(a)} all find SUM.
     */
    private String aggregateLookupKey(final String written) {
        return SqlIdentifiers.canonicalText(written).toUpperCase(Locale.ROOT);
    }

    private boolean containsAggregate(final ParseTree node) {
        if (node == null) {
            return false;
        }
        if (node instanceof FrostlakeParser.SelectStatementContext
                || node instanceof FrostlakeParser.OverClauseContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.FunctionCallExprContext) {
            final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) node;
            // A call WITH an OVER clause is a WINDOW function, not an aggregate (its OVER child is pruned
            // above); one without is a candidate aggregate.
            if (funcCtx.overClause() == null
                    && executor.getFunctionRegistry().hasAggregateFunction(
                        aggregateLookupKey(funcCtx.functionName().getText()))) {
                return true;
            }
        }
        if (node instanceof FrostlakeParser.FunctionCallStarExprContext) {
            final FrostlakeParser.FunctionCallStarExprContext funcCtx = (FrostlakeParser.FunctionCallStarExprContext) node;
            if (executor.getFunctionRegistry().hasAggregateFunction(
                    aggregateLookupKey(funcCtx.functionName().getText()))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (containsAggregate(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    boolean hasWindowFunction(final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) continue;
            if (hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                return true;
            }
        }
        return false;
    }

    boolean hasWindowFunctionInExpression(final FrostlakeParser.ExpressionContext expr) {
        if (expr == null) {
            return false;
        }
        // A window call anywhere in the expression counts — not only when the whole item IS a bare
        // fn(...) OVER (...) call, but also when one is nested in arithmetic/boolean (revenue - LAG()OVER()).
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        collectWindowFunctionCalls(expr, windowCalls);
        return !windowCalls.isEmpty();
    }

    /** True when the parse subtree contains a window call ({@code fn(...) OVER (...)}) anywhere. */
    boolean hasWindowFunctionInTree(final ParseTree node) {
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        collectWindowFunctionCalls(node, windowCalls);
        return !windowCalls.isEmpty();
    }

    Map<Integer, Map<Integer, Object>> computeWindowFunctions(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx, final Table table) {
        return computeWindowFunctions(rows, ctx, table, false, table);
    }

    Map<Integer, Map<Integer, Object>> computeWindowFunctions(final List<Row> rows, final FrostlakeParser.SelectClauseContext ctx, final Table table, final boolean rowsAreProjected, final Table baseTable) {
        // Returns: Map<rowIndex, Map<selectItemIndex, windowFunctionResult>>
        final Map<Integer, Map<Integer, Object>> results = new HashMap<>();

        // Expose this query's SELECT aliases so a PARTITION BY / window ORDER BY can reference one; saved and
        // restored so a nested subquery's window computation doesn't leak its aliases back out.
        final Map<String, String> savedAliases = beginWindowAliasScope(ctx, rowsAreProjected);
        try {

        // Plan-time: a FILE argument to a window aggregate is rejected before any row is computed.
        final List<FrostlakeParser.FunctionCallExprContext> selectWindowCalls = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isExprItem(item)) {
                collectWindowFunctionCalls(SelectItemAccessors.getItemValueExpr(item), selectWindowCalls);
            }
        }
        // Over GROUPED rows the shape carries the SELECT list, not the FROM columns, so a window
        // ARGUMENT is judged against the BASE relation — LAG(MAX(c)) OVER (…) reads c from the rows
        // behind the grouping, and live runs it. Whether that reference is GROUPED is a different
        // question, answered by the grouped select-list validator.
        rejectFileWindowArguments(selectWindowCalls, rowsAreProjected ? baseTable : table,
            executor.selectItemAliasNames(ctx));
        // …and so is a FILE or GEOSPATIAL key in the OVER spec. The per-partition call below only runs
        // once a partition is actually built, which never happens over an EMPTY input — while live
        // rejects the query at COMPILE time either way ("Expressions of type GEOGRAPHY cannot be used
        // as PARTITION BY keys", SQLSTATE 42804, on a table with no rows as on a populated one).
        for (final FrostlakeParser.FunctionCallExprContext call : selectWindowCalls) {
            if (call.overClause() != null) {
                rejectFileWindowKeys(call.overClause(), table);
            }
        }

        // Per OVER clause: rows grouped into partitions (PARTITION BY) and each partition sorted
        // (ORDER BY) exactly once, then reused across every row — instead of re-partitioning/re-sorting
        // per row. Built lazily on first use; see sortedPartitionForRow.
        final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache = new HashMap<>();
        resetPartitionOrderCache();

        // Classify each select item ONCE — whether it carries a window call (a tree walk) is a
        // per-STATEMENT fact the row loop used to re-derive per row per item.
        final List<FrostlakeParser.SelectItemContext> selectItems = ctx.selectList().selectItem();
        final FrostlakeParser.ExpressionContext[] windowItemExprs =
            new FrostlakeParser.ExpressionContext[selectItems.size()];
        for (int itemIdx = 0; itemIdx < selectItems.size(); itemIdx++) {
            final FrostlakeParser.SelectItemContext item = selectItems.get(itemIdx);
            if (SelectItemAccessors.isExprItem(item)
                    && hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                windowItemExprs[itemIdx] = SelectItemAccessors.getItemValueExpr(item);
            }
        }

        // For each row, compute window function values
        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            final Map<Integer, Object> rowResults = new HashMap<>();
            for (int itemIdx = 0; itemIdx < windowItemExprs.length; itemIdx++) {
                if (windowItemExprs[itemIdx] != null) {
                    final Object value = evaluateWindowFunction(windowItemExprs[itemIdx], rows, rowIdx, ctx, table, overCache);
                    rowResults.put(itemIdx, value);
                }
            }

            results.put(rowIdx, rowResults);
        }

        return results;
        } finally {
            endWindowAliasScope(savedAliases);
        }
    }

    /** Make this SELECT's aliases resolvable in PARTITION BY / window ORDER BY keys for the duration of a
     *  window computation. Returns the previous scope to pass to {@link #endWindowAliasScope}. Used by both
     *  the SELECT-list window computation and the QUALIFY inline-window computation. */
    /** Each RATIO_TO_REPORT call's declared scale, so the width is inferred once and not once per row. */
    private final Map<FrostlakeParser.FunctionCallExprContext, Integer> ratioDeclaredScales =
        new IdentityHashMap<>();

    Map<String, String> beginWindowAliasScope(final FrostlakeParser.SelectClauseContext ctx) {
        return beginWindowAliasScope(ctx, false);
    }

    /**
     * As {@link #beginWindowAliasScope(FrostlakeParser.SelectClauseContext)}; with
     * {@code rowsAreProjected} the rows handed to the window stage are already in SELECT-list shape
     * (grouped/aggregated), so ALSO index each select item by its canonical AST so a PARTITION BY /
     * ORDER BY key that IS one of the items — typically a raw aggregate, OVER (ORDER BY SUM(x)) —
     * resolves to the item's already-computed value positionally.
     */
    Map<String, String> beginWindowAliasScope(final FrostlakeParser.SelectClauseContext ctx,
                                              final boolean rowsAreProjected) {
        final Map<String, String> saved = windowSelectAliases;
        savedCanonicalScopes.push(windowSelectItemCanonicalIndex);
        windowSelectAliases = buildSelectAliasMap(ctx);
        windowSelectItemCanonicalIndex =
            rowsAreProjected ? buildSelectItemCanonicalIndex(ctx) : new HashMap<>();
        return saved;
    }

    /**
     * Install the group resolver for the computation about to run, answering the previous one so the
     * caller can restore it.
     *
     * @param values the resolver, or null when the rows are not grouped
     * @return the resolver that was installed before
     */
    /**
     * An evaluator over {@code table} that, over GROUPED rows, reads DECLARED types from the group's
     * base relation. The projected shape the window stage evaluates against carries values in
     * SELECT-list slots, and a slot the projection could not type — or a base column the SELECT list
     * never projected — has no declaration there; on the account {@code RATIO_TO_REPORT(a) OVER ()}
     * beside {@code GROUP BY a} is NUMBER(18,8) because {@code a} is the NUMBER(10,2) column it always
     * was. Without this every window argument over grouped rows was untyped, so SYSTEM$TYPEOF answered
     * NULL for LAG, SUM, AVG, MAX and FIRST_VALUE alike and RATIO_TO_REPORT kept a nominal width.
     *
     * @param table the relation the values are read from
     * @return the evaluator
     */
    private ExpressionEvaluator evaluatorOver(final Table table) {
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        if (groupedValues != null && groupedValues.baseTable() != null
                && groupedValues.baseTable() != table) {
            evaluator.setDeclaredTypeBase(groupedValues.baseTable(), groupedValues.aliasToTable(),
                groupedValues.allTables());
        }
        return evaluator;
    }

    GroupedExpressionValues beginGroupedValues(final GroupedExpressionValues values) {
        final GroupedExpressionValues saved = groupedValues;
        groupedValues = values;
        return saved;
    }

    /**
     * Restore the resolver saved by {@link #beginGroupedValues}.
     *
     * @param saved the resolver to put back
     */
    void endGroupedValues(final GroupedExpressionValues saved) {
        groupedValues = saved;
    }

    /**
     * An expression's value for a grouped row, computed over its source group, or {@code UNRESOLVED}
     * when there is no group resolver, no group, or nothing the group can answer.
     *
     * @param exprText the expression as written
     * @param row      the row being valued
     * @return the value, or {@link GroupedExpressionValues#UNRESOLVED}
     */
    private Object groupedValueOf(final String exprText, final Row row) {
        if (groupedValues == null || exprText == null || row == null) {
            return GroupedExpressionValues.UNRESOLVED;
        }
        return groupedValues.valueOf(row, exprText);
    }

    /**
     * A window function ARGUMENT's value for one row. Over grouped rows the argument may be a raw
     * aggregate — {@code LAG(SUM(b))}, {@code SUM(SUM(b)) OVER ()} — which the projected row does not
     * carry, so the group answers it; everything else evaluates as it always did.
     *
     * @param argExpr the argument as written
     * @param row     the row being valued
     * @param table   the shape the row is in
     * @return the argument's value
     */
    private Object argumentValue(final String argExpr, final Row row, final Table table) {
        final Object grouped = groupedValueOf(argExpr, row);
        if (grouped != GroupedExpressionValues.UNRESOLVED) {
            return grouped;
        }
        return executor.evaluateExpression(argExpr, row, table);
    }

    /** Restore the alias scope saved by {@link #beginWindowAliasScope}. */
    void endWindowAliasScope(final Map<String, String> saved) {
        windowSelectAliases = saved;
        windowSelectItemCanonicalIndex =
            savedCanonicalScopes.isEmpty() ? new HashMap<>() : savedCanonicalScopes.pop();
    }

    /**
     * Canonical AST print of each SELECT item's expression → its projected column index. Empty when a
     * star/spread item makes positions unpredictable. First occurrence wins on duplicates.
     */
    private Map<String, Integer> buildSelectItemCanonicalIndex(final FrostlakeParser.SelectClauseContext ctx) {
        final Map<String, Integer> index = new HashMap<>();
        final List<FrostlakeParser.SelectItemContext> items = ctx.selectList().selectItem();
        for (int i = 0; i < items.size(); i++) {
            final FrostlakeParser.SelectItemContext item = items.get(i);
            if (!SelectItemAccessors.isExprItem(item)) {
                return new HashMap<>();
            }
            try {
                final String canonical = AstPrinterVisitor.print(ExpressionEvaluator.parse(
                    ParseTreeText.getOriginalText(SelectItemAccessors.getItemValueExpr(item))));
                if (!index.containsKey(canonical)) {
                    index.put(canonical, i);
                }
            } catch (final RuntimeException unparseable) {
                // Leave this item unmatched; the generic evaluation paths still apply.
            }
        }
        return index;
    }

    /** Map each non-windowed SELECT item's alias (canonical) to its defining expression text, so a window
     *  PARTITION BY / ORDER BY key that names an alias can resolve to that expression. Windowed items are
     *  excluded — a partition/order key can't circularly reference the window function it partitions. */
    private Map<String, String> buildSelectAliasMap(final FrostlakeParser.SelectClauseContext ctx) {
        final Map<String, String> aliases = new HashMap<>();
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                continue;
            }
            final String aliasCtx = SelectItemAccessors.getItemAlias(item);
            if (aliasCtx == null || hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                continue;
            }
            aliases.put(aliasCtx,
                ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item)));
        }
        return aliases;
    }


    /** How many slots a star item occupies in a grouped (projected) row — its expanded column count. */
    private int starSlotWidth(final FrostlakeParser.SelectItemContext item, final Table table) {
        if (SelectItemAccessors.isStarItem(item)) {
            return executor.bareStarExpressions(item, table).size();
        }
        return executor.starItemColumns(item, table, Collections.emptyMap()).size();
    }


    /**
     * One ORDER BY key that the SELECT list does not carry, computed for a row of the source.
     *
     * <p>Ordinarily that is a FROM column the projection is about to drop, and evaluating its text
     * against the source row is the whole job. A key that CONTAINS A WINDOW CALL is different: no
     * evaluator can produce that value from one row, because a window reads the whole partition. Each
     * call in the key is therefore computed by the window machinery and published under its own source
     * text, which is exactly how the parsed key refers to it — so
     * {@code ORDER BY ROW_NUMBER() OVER (ORDER BY b)} and
     * {@code ORDER BY ROW_NUMBER() OVER (ORDER BY b) + 1} both resolve, the second by evaluating the
     * arithmetic around a value that is already known.
     *
     * <p>Over GROUPED rows the source row is not a source row at all — it is one row per group, already
     * in SELECT-list shape — so a key naming anything the SELECT list does not project has nowhere to
     * resolve against it. {@code GROUP BY a ORDER BY a} and {@code ORDER BY MAX(b)} are both perfectly
     * legal there, and the group each output row came from is the only place either value exists, so it
     * is asked first and the row-shaped evaluation is kept as the fallback.
     */
    private Object extraOrderKeyValue(final String keyText,
                                      final FrostlakeParser.ExpressionContext keyTree,
                                      final List<Row> rows, final int rowIdx,
                                      final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                      final Row originalRow,
                                      final Map<FrostlakeParser.OverClauseContext,
                                          Map<List<Object>, List<Row>>> overCache) {
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        if (keyTree != null) {
            collectWindowFunctionCalls(keyTree, windowCalls);
        }
        if (windowCalls.isEmpty()) {
            final Object overGroup = groupedValueOf(keyText, originalRow);
            if (overGroup != GroupedExpressionValues.UNRESOLVED) {
                return overGroup;
            }
            return executor.evaluateExpression(keyText, originalRow, table);
        }
        final Map<String, Object> windowValues = new HashMap<>();
        for (final FrostlakeParser.FunctionCallExprContext call : windowCalls) {
            windowValues.put(ParseTreeText.getOriginalText(call),
                evaluateWindowFunction(call, rows, rowIdx, ctx, table, overCache));
        }
        final ExpressionEvaluator keyEvaluator = evaluatorOver(table);
        keyEvaluator.setResultContext(windowValues);
        return keyEvaluator.evaluate(ExpressionEvaluator.parse(keyText), originalRow);
    }

    List<Row> addWindowFunctionsToRows(final List<Row> rows,
                                                final Map<Integer, Map<Integer, Object>> windowFunctionResults,
                                                final FrostlakeParser.SelectClauseContext ctx,
                                                final Table table,
                                                final Table baseTable,
                                                final boolean rowsAreProjected,
                                                final List<String> extraOrderKeyExprs,
                                                final List<FrostlakeParser.ExpressionContext> extraOrderKeyTrees,
                                                final Map<Row, Object[]> extraKeyValuesOut) {
        final List<Row> resultRows = new ArrayList<>();
        final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> extraKeyOverCache =
            new HashMap<>();

        for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
            final Row originalRow = rows.get(rowIdx);
            final List<Object> values = new ArrayList<>();

            // This loop IS the projection for a windowed query (QueryExecutor skips ProjectOperator when the
            // SELECT list has a window function), so it must offer the same Snowflake lateral column aliases:
            // each aliased item's value is published here for LATER items to reference, e.g.
            // `ROUND(…) AS score_band, CASE WHEN score_band > 8.9 THEN … END, ROW_NUMBER() OVER (…) AS rn`.
            // Without this an alias reference threw "Column not found" for the whole query. Fresh per row.
            final Map<String, Object> lateralAliases = new HashMap<>();

            // Add values for each select item. A grouped (projected) row carries one value per
            // EXPANDED output column — a star spans several slots there — so the read cursor into
            // it advances by each item's width, independently of the item index that keys the
            // window results.
            int selectItemIdx = 0;
            int projectedSlot = 0;
            for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
                if (!SelectItemAccessors.isExprItem(item)) {
                    if (rowsAreProjected) {
                        // The grouped projection already produced this item's value(s); a star was
                        // expanded to its columns, so copy its whole width to keep the layout aligned.
                        final int width = SelectItemAccessors.isStarItem(item)
                                || SelectItemAccessors.isQualifiedStarItem(item)
                            ? starSlotWidth(item, baseTable) : 1;
                        for (int s = 0; s < width && projectedSlot < originalRow.getValues().size(); s++) {
                            values.add(originalRow.getValue(projectedSlot));
                            projectedSlot++;
                        }
                    } else if (SelectItemAccessors.isObjectStarItem(item)) {
                        // The braced star is ONE object over the row's columns, not the columns themselves.
                        values.add(executor.evaluateExpression(
                            executor.objectStarExpression(item, table, null), originalRow, table));
                    } else if (SelectItemAccessors.isStarItem(item)) {
                        // A bare star projects its effective columns one by one — never the raw row,
                        // which for a USING / NATURAL join is WIDER than the star's column list (the
                        // hidden right-side key duplicates) and would misalign every later slot.
                        for (final String starExpr : executor.bareStarExpressions(item, table)) {
                            values.add(executor.evaluateExpression(starExpr, originalRow, table));
                        }
                    } else {
                        // A qualified star (t.*) expands to the source row's columns. Dropping them left
                        // the projected row holding only the window values while the result metadata kept
                        // every column — any later positional read (an outer SELECT over the CTE)
                        // indexed past the row's end.
                        values.addAll(originalRow.getValues());
                    }
                    selectItemIdx++;
                    continue;
                }
                final Object value;
                final String exprText = ParseTreeText.getOriginalText(SelectItemAccessors.getItemExpression(item));
                final String bare = exprText.trim().toUpperCase();
                if (hasWindowFunctionInExpression(SelectItemAccessors.getItemValueExpr(item))) {
                    final Map<Integer, Object> rowWindowResults = windowFunctionResults.get(rowIdx);
                    value = rowWindowResults != null && rowWindowResults.containsKey(selectItemIdx)
                        ? rowWindowResults.get(selectItemIdx) : null;
                    if (rowsAreProjected) {
                        projectedSlot++;   // the grouped row holds a placeholder slot for this item
                    }
                } else if (rowsAreProjected) {
                    // GROUP BY / implicit aggregation already computed every non-window item — the row IS the
                    // SELECT-list shape. Take the value positionally: re-evaluating the item's text here sent
                    // aggregate calls (ARRAY_AGG(…)) to the scalar evaluator, which failed with
                    // "Unknown function", and would recompute expressions against the wrong table anyway.
                    value = projectedSlot < originalRow.getValues().size()
                        ? originalRow.getValue(projectedSlot) : null;
                    projectedSlot++;
                } else if (lateralAliases.containsKey(bare) && !table.hasColumn(bare)) {
                    // The item IS an earlier alias: reuse that value. Recomputing is not an option when the
                    // defining item was a window function. A real column of the same name still wins.
                    value = lateralAliases.get(bare);
                } else {
                    value = executor.evaluateExpression(exprText, originalRow, table, lateralAliases);
                }
                values.add(value);
                final String alias = SelectItemAccessors.getItemAlias(item);
                if (alias != null) {
                    lateralAliases.put(alias.toUpperCase(), value);
                }
                selectItemIdx++;
            }

            final Row projected = new Row(values);
            resultRows.add(projected);

            // Projection drops columns not in the SELECT list, but ORDER BY may reference a FROM column that
            // is not selected. Precompute those keys now (the FROM columns are still on originalRow), keyed by
            // the projected row instance so they survive a later QUALIFY filter and can be used for sorting.
            if (extraOrderKeyExprs != null && !extraOrderKeyExprs.isEmpty()) {
                final Object[] keyVals = new Object[extraOrderKeyExprs.size()];
                for (int e = 0; e < extraOrderKeyExprs.size(); e++) {
                    keyVals[e] = extraOrderKeyValue(extraOrderKeyExprs.get(e),
                        extraOrderKeyTrees == null || e >= extraOrderKeyTrees.size()
                            ? null : extraOrderKeyTrees.get(e),
                        rows, rowIdx, ctx, table, originalRow, extraKeyOverCache);
                }
                extraKeyValuesOut.put(projected, keyVals);
            }
        }

        return resultRows;
    }

    Object evaluateWindowFunction(final FrostlakeParser.ExpressionContext expr,
                                          final List<Row> allRows, final int currentRowIndex,
                                          final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                          final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        if (!(expr instanceof FrostlakeParser.FunctionCallExprContext)) {
            // A window function nested inside a larger expression, e.g. revenue - LAG(...) OVER (...).
            return evaluateNestedWindowExpression(expr, allRows, currentRowIndex, ctx, table, overCache);
        }

        final FrostlakeParser.FunctionCallExprContext funcCtx = (FrostlakeParser.FunctionCallExprContext) expr;
        final String functionName = funcCtx.functionName().getText().toUpperCase();
        final FrostlakeParser.OverClauseContext overClause = funcCtx.overClause();

        if (overClause == null) {
            // A plain function call with no OVER of its own, but its arguments may contain window calls
            // (e.g. ABS(LAG(x) OVER (...))); handle it through the nested path.
            return evaluateNestedWindowExpression(expr, allRows, currentRowIndex, ctx, table, overCache);
        }

        // Only names WindowFunctionNames declares (or a registered aggregate, which the default branch
        // below runs over the frame) are window functions. Rejecting anything else HERE, before the
        // partition work and before the switch, is what makes that set load-bearing: it is the same set
        // SHOW FUNCTIONS enumerates via FunctionRegistry.allDispatchableNames(), so a case added below
        // without declaring the name simply does not dispatch — the listing cannot silently fall behind
        // the switch again, which is how ROW_NUMBER, RANK, LAG and 11 others ended up unlisted.
        if (!WindowFunctionNames.handles(functionName)
                && executor.getFunctionRegistry().getAggregateFunction(functionName) == null) {
            throw new RuntimeException("Unsupported window function: " + functionName);
        }

        // The rank family takes NO arguments (measured: ROW_NUMBER(1) refuses as
        // "too many arguments for function [ROW_NUMBER(1)] expected 0, got 1").
        if (ZERO_ARGUMENT_WINDOW_FUNCTIONS.contains(functionName)
                && funcCtx.functionArgList() != null
                && !funcCtx.functionArgList().functionArg().isEmpty()) {
            final StringBuilder rendered = new StringBuilder();
            for (final FrostlakeParser.FunctionArgContext argCtx : funcCtx.functionArgList().functionArg()) {
                if (rendered.length() > 0) {
                    rendered.append(", ");
                }
                rendered.append(executor.getOriginalText(argCtx));
            }
            throw new RuntimeException("too many arguments for function [" + functionName
                + "(" + rendered + ")] expected 0, got "
                + funcCtx.functionArgList().functionArg().size());
        }

        // The current row's partition (PARTITION BY), sorted by ORDER BY — grouped and sorted once per
        // OVER clause and cached, then shared read-only by every function for this window.
        final List<Row> sortedPartition =
            sortedPartitionForRow(overClause, allRows.get(currentRowIndex), allRows, table, overCache);

        // Compute window function based on type
        switch (functionName) {
            case "ROW_NUMBER":
                return computeRowNumber(sortedPartition, currentRowIndex, overClause, allRows, table);
            case "RANK":
                return computeRank(sortedPartition, currentRowIndex, overClause, allRows, table);
            case "DENSE_RANK":
                return computeDenseRank(sortedPartition, currentRowIndex, overClause, allRows, table);
            case "LAG":
                return computeLag(funcCtx, sortedPartition, currentRowIndex, overClause, allRows, table);
            case "LEAD":
                return computeLead(funcCtx, sortedPartition, currentRowIndex, overClause, allRows, table);
            case "COUNT": {
                final int[] frame = frameBounds(overClause, sortedPartition, allRows.get(currentRowIndex), table);
                return computeWindowCount(funcCtx, sortedPartition, frame[0], frame[1], table);
            }
            case "SUM":
            case "AVG":
            case "MIN":
            case "MAX": {
                final int[] frame = frameBounds(overClause, sortedPartition, allRows.get(currentRowIndex), table);
                return computeWindowAggregate(functionName, funcCtx, sortedPartition, frame[0], frame[1], table);
            }
            case "NTILE":
                return computeNtile(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            case "PERCENT_RANK":
                return computePercentRank(sortedPartition, allRows.get(currentRowIndex), overClause, table);
            case "CUME_DIST":
                return computeCumeDist(sortedPartition, allRows.get(currentRowIndex), overClause, table);
            case "RATIO_TO_REPORT":
                return computeRatioToReport(funcCtx, sortedPartition, allRows.get(currentRowIndex), table);
            case "FIRST_VALUE": {
                final int[] frame = valueFrameBounds(functionName, overClause, sortedPartition, allRows.get(currentRowIndex), table);
                return computeFirstValue(funcCtx, sortedPartition, frame[0], frame[1], table);
            }
            case "LAST_VALUE": {
                final int[] frame = valueFrameBounds(functionName, overClause, sortedPartition, allRows.get(currentRowIndex), table);
                return computeLastValue(funcCtx, sortedPartition, frame[0], frame[1], table);
            }
            case "NTH_VALUE": {
                final int[] frame = valueFrameBounds(functionName, overClause, sortedPartition, allRows.get(currentRowIndex), table);
                return computeNthValue(funcCtx, sortedPartition, frame[0], frame[1], table);
            }
            case "CONDITIONAL_TRUE_EVENT":
                return computeConditionalTrueEvent(funcCtx, sortedPartition, allRows.get(currentRowIndex), overClause, table);
            case "CONDITIONAL_CHANGE_EVENT":
                return computeConditionalChangeEvent(funcCtx, sortedPartition, allRows.get(currentRowIndex), overClause, table);
            default:
                // Any registered aggregate is usable as a window function over the frame — Snowflake allows
                // e.g. ARRAY_AGG(x) OVER (PARTITION BY g), LISTAGG, MEDIAN, … — evaluated with the same
                // accumulator the grouped path uses. DISTINCT is honoured.
                final AggregateFunction genericAgg =
                    executor.getFunctionRegistry().getAggregateFunction(functionName);
                if (genericAgg != null) {
                    return computeGenericWindowAggregate(genericAgg, funcCtx,
                        frameRows(overClause, sortedPartition, allRows.get(currentRowIndex), table), table);
                }
                throw new RuntimeException("Unsupported window function: " + functionName);
        }
    }

    /**
     * Evaluate an expression that CONTAINS one or more window-function calls but is not itself a bare
     * window call — e.g. {@code revenue - LAG(revenue) OVER (...)} or {@code 100 * RATIO_TO_REPORT(x)
     * OVER (...)}. Each window call is computed for the current row; in the parsed expression it is a
     * {@code WindowFunctionExpression} node (keyed by its source text), and the surrounding
     * arithmetic/boolean is evaluated with those nodes resolved from the result context under the same
     * key — the technique QUALIFY uses for inline window predicates. Returns null when the expression
     * contains no window call.
     */
    private Object evaluateNestedWindowExpression(final FrostlakeParser.ExpressionContext expr,
                                                  final List<Row> allRows, final int currentRowIndex,
                                                  final FrostlakeParser.SelectClauseContext ctx, final Table table,
                                                  final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        final List<FrostlakeParser.FunctionCallExprContext> windowCalls = new ArrayList<>();
        collectWindowFunctionCalls(expr, windowCalls);
        if (windowCalls.isEmpty()) {
            return null;
        }
        final String exprText = ParseTreeText.getOriginalText(expr);
        // Each nested window call keeps its place in the expression AST (as a WindowFunctionExpression node
        // keyed by its source text); supply its per-row value through the result context under that same key
        // rather than string-substituting a synthetic name into the text and re-parsing.
        final Map<String, Object> resultContext = new HashMap<>();
        for (final FrostlakeParser.FunctionCallExprContext wfn : windowCalls) {
            final Object windowValue = evaluateWindowFunction(wfn, allRows, currentRowIndex, ctx, table, overCache);
            resultContext.put(ParseTreeText.getOriginalText(wfn), windowValue);
        }
        final ExpressionEvaluator ev = evaluatorOver(table);
        // Over a JOIN's rows, resolve the non-window parts (e.g. o.region in
        // o.region || ROW_NUMBER() OVER (...)) with that join's alias context.
        final Map<String, Table> winAliasToTable = executor.currentWindowAliasToTable();
        if (winAliasToTable != null) {
            ev.setMultiTableContext(winAliasToTable, executor.currentWindowAllTables());
        }
        ev.setResultContext(resultContext);
        return ev.evaluate(exprText, allRows.get(currentRowIndex));
    }

    /**
     * CONDITIONAL_TRUE_EVENT(expr) OVER([PARTITION BY …] ORDER BY …) — a running count that starts at 0
     * and increments by 1 on every row (in ORDER BY order, up to and including the current row) on which
     * {@code expr} evaluates to TRUE. A row on which expr is not TRUE (including NULL) carries the current
     * count. Returns a long.
     */
    private Long computeConditionalTrueEvent(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                             final List<Row> sortedPartition, final Row currentRow,
                                             final FrostlakeParser.OverClauseContext overClause,
                                             final Table table) {
        final String argExpr = conditionalEventArg(funcCtx);
        final int pos = conditionalEventPosition(sortedPartition, currentRow, overClause, table);
        final Object[] vector = frameArgVector(sortedPartition, argExpr, table, VECTOR_MODE_EXPRESSION);
        long count = 0L;
        for (int i = 0; i <= pos; i++) {
            final Object v = vector != null ? vector[i]
                : argumentValue(argExpr, sortedPartition.get(i), table);
            if (isTruthy(v)) {
                count++;
            }
        }
        return count;
    }

    /** The current row's 0-based partition position for the CONDITIONAL_* prefix walk — the same
     *  first-equal anchor (with the same last-index fallback) the frame computation uses. */
    private int conditionalEventPosition(final List<Row> sortedPartition, final Row currentRow,
                                         final FrostlakeParser.OverClauseContext overClause, final Table table) {
        final Long anchor = orderMapsFor(sortedPartition, overClause, table).position(currentRow);
        return anchor != null ? (int) (anchor.longValue() - 1) : sortedPartition.size() - 1;
    }

    /**
     * CONDITIONAL_CHANGE_EVENT(expr) OVER([PARTITION BY …] ORDER BY …) — a running count that starts at 0
     * on the first row of the partition and increments by 1 each time {@code expr}'s value differs from
     * the previous row's value (in ORDER BY order, up to and including the current row). Per Snowflake,
     * "NULL values are not considered a new or changed value": a step in which either the current or the
     * previous value is NULL is not counted as a change. Returns a long.
     */
    private Long computeConditionalChangeEvent(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                               final List<Row> sortedPartition, final Row currentRow,
                                               final FrostlakeParser.OverClauseContext overClause,
                                               final Table table) {
        final String argExpr = conditionalEventArg(funcCtx);
        final int pos = conditionalEventPosition(sortedPartition, currentRow, overClause, table);
        final Object[] vector = frameArgVector(sortedPartition, argExpr, table, VECTOR_MODE_EXPRESSION);
        long count = 0L;
        Object prev = sortedPartition.isEmpty() ? null
            : (vector != null ? vector[0] : argumentValue(argExpr, sortedPartition.get(0), table));
        for (int i = 1; i <= pos; i++) {
            final Object cur = vector != null ? vector[i]
                : argumentValue(argExpr, sortedPartition.get(i), table);
            if (prev != null && cur != null && ValueComparisons.compareValues(cur, prev) != 0) {
                count++;
            }
            prev = cur;
        }
        return count;
    }

    /** The single argument expression text of a CONDITIONAL_*_EVENT window call. */
    private static String conditionalEventArg(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        if (windowArgs(funcCtx).isEmpty()) {
            throw new RuntimeException("CONDITIONAL_TRUE_EVENT / CONDITIONAL_CHANGE_EVENT requires one argument");
        }
        return ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0));
    }

    /** Snowflake truthiness of a CONDITIONAL_TRUE_EVENT argument: TRUE, or a non-zero number. */
    private static boolean isTruthy(final Object value) {
        if (value == null) return false;
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof Number) return ((Number) value).doubleValue() != 0.0;
        final String text = value.toString().trim();
        return text.equalsIgnoreCase("true") || text.equals("1");
    }

    /**
     * The sorted partition that contains {@code currentRow}, for a given OVER clause. Partitions
     * (PARTITION BY) and their ORDER BY sort are built exactly once per OVER clause and cached in
     * {@code overCache}, then reused for every row of the window computation. With no PARTITION BY there
     * is a single partition (all rows); with no ORDER BY the partition keeps its original order.
     */
    private List<Row> sortedPartitionForRow(final FrostlakeParser.OverClauseContext overClause,
                                            final Row currentRow, final List<Row> allRows, final Table table,
                                            final Map<FrostlakeParser.OverClauseContext, Map<List<Object>, List<Row>>> overCache) {
        Map<List<Object>, List<Row>> partitions = overCache.get(overClause);
        if (partitions == null) {
            partitions = buildSortedPartitions(overClause, allRows, table);
            overCache.put(overClause, partitions);
        }
        final List<Row> partition = partitions.get(partitionKey(currentRow, overClause.partitionByClause(), table));
        // currentRow is one of allRows, so its key is always present; fall back defensively.
        return partition != null ? partition : allRows;
    }

    /**
     * Group all rows into partitions by the PARTITION BY key (a single partition when absent) and sort
     * each partition by the OVER ORDER BY. The sorted partition lists are shared read-only by callers.
     */
    private Map<List<Object>, List<Row>> buildSortedPartitions(final FrostlakeParser.OverClauseContext overClause,
                                                               final List<Row> allRows, final Table table) {
        rejectFileWindowKeys(overClause, table);
        final Map<List<Object>, List<Row>> groups = new LinkedHashMap<>();
        for (final Row row : allRows) {
            final List<Object> key = partitionKey(row, overClause.partitionByClause(), table);
            List<Row> group = groups.get(key);
            if (group == null) {
                group = new ArrayList<>();
                groups.put(key, group);
            }
            group.add(row);
        }
        if (overClause.orderByClause() != null) {
            for (final Map.Entry<List<Object>, List<Row>> entry : groups.entrySet()) {
                entry.setValue(sortRowsForWindow(entry.getValue(), overClause.orderByClause(), table));
            }
        }
        return groups;
    }

    /**
     * A window spec's keys are subject to the same FILE rule as the statement-level clauses: live
     * {@code OVER (PARTITION BY f)} is "Expressions of type FILE cannot be used as
     * PARTITION BY keys" and {@code OVER (ORDER BY f)} the ORDER BY variant. Checked once per OVER
     * clause — this method runs from the partition build, which is itself cached per clause — so every
     * window path (SELECT list and inline QUALIFY alike) is covered by the one call.
     */
    /**
     * A window aggregate is subject to the same FILE rule as its plain form: live,
     * {@code MAX(f) OVER ()} fails "Function MAX does not support FILE argument type" exactly as
     * {@code MAX(f)} does. The plan-time projection walk cannot see it — a windowed call parses to a
     * {@code WindowFunctionExpression} the walk does not descend into — so the call is re-formed here
     * WITHOUT its OVER clause and put through the ordinary strict-argument checks. Called once per
     * query per window call, before any row is evaluated.
     */
    void rejectFileWindowArguments(final List<FrostlakeParser.FunctionCallExprContext> windowCalls,
                                   final Table table, final Set<String> outputAliasNames) {
        final ExpressionEvaluator checker = evaluatorOver(table);
        // A window argument may reference a SELECT alias (SUM(w) OVER () beside v AS w works
        // live), so the output aliases are exempt from the walk's scope rejection.
        checker.setScopeExemptNames(outputAliasNames);
        for (final FrostlakeParser.FunctionCallExprContext call : windowCalls) {
            // The call's DECLARED type is asked for before any row is computed, because the type can
            // itself be the refusal: AVG(x) OVER () over a NUMBER(38,37) is "Invalid intermediate
            // datatype: NUMBER(41,40)." at compile time on the account, ahead of any value. The
            // facade lets a compilation error through and swallows an ordinary undetermined type.
            checker.inferStaticType(ExpressionEvaluator.parse(ParseTreeText.getOriginalText(call)));
            final List<FrostlakeParser.BooleanExprContext> args = windowArgs(call);
            if (args.isEmpty()) {
                // A windowed BARE STAR — SUM(*) OVER () — has no booleanExpr argument to re-form,
                // but live holds it to the same EXPANDED arity as the plain star call, so the star
                // call is re-formed and walked like the rest.
                rejectStarWindowArgument(call, checker);
                rejectEmptyRewrittenCall(call, checker);
                continue;
            }
            // The argument list is copied VERBATIM, spacing and all, so every argument keeps its
            // distance from the one before it; joining them with a fixed ", " moved the second
            // argument of `LEAD(a,nosuchcol)` by a character. The origin is then anchored so the
            // FIRST argument lands where it stands in the statement — live points at the argument,
            // not at the call, and not at the function name.
            final Token firstArg = args.get(0).getStart();
            final Token lastArg = args.get(args.size() - 1).getStop();
            final String name = call.functionName().getText();
            final String argsAsWritten = firstArg.getInputStream() == null ? null
                : firstArg.getInputStream().getText(
                    new Interval(firstArg.getStartIndex(), lastArg.getStopIndex()));
            final int anchor = firstArg.getCharPositionInLine() - (name.length() + 1);
            final SourcePosition displacedCall = ExpressionSource.beginNested(anchor >= 0
                ? new SourcePosition(firstArg.getLine(), anchor)
                : new SourcePosition(call.getStart().getLine(), call.getStart().getCharPositionInLine()));
            try {
                if (argsAsWritten != null) {
                    checker.validateStrictWindowed(
                        ExpressionEvaluator.parse(walkedName(name) + "(" + argsAsWritten + ")"));
                } else {
                    final List<String> argTexts = new ArrayList<>();
                    for (final FrostlakeParser.BooleanExprContext arg : args) {
                        argTexts.add(ParseTreeText.getOriginalText(arg));
                    }
                    checker.validateStrictWindowed(ExpressionEvaluator.parse(
                        walkedName(name) + "(" + String.join(", ", argTexts) + ")"));
                }
            } finally {
                ExpressionSource.end(displacedCall);
            }
        }
    }

    /**
     * The name a window call is WALKED under. RATIO_TO_REPORT is rewritten by the account into a
     * division by SUM, and every refusal it earns is worded in SUM's terms: a DATE argument is
     * "Invalid argument types for function 'SUM': (DATE)", a second argument is "too many arguments
     * for function [SUM(1, 2)] expected 1, got 2", both anchored at the call. Walking the re-formed
     * call as SUM gives it SUM's arity, SUM's argument families and SUM's name in the echo at once.
     * The replacement is padded to the written name's length so every argument keeps its offset in
     * the statement — the walk positions an argument's own refusal at the argument.
     *
     * @param written the function name as written
     * @return the name to walk it under, padded to the written width
     */
    private static String walkedName(final String written) {
        if (!"RATIO_TO_REPORT".equalsIgnoreCase(written)) {
            return written;
        }
        final StringBuilder padded = new StringBuilder("SUM");
        while (padded.length() < written.length()) {
            padded.append(' ');
        }
        return padded.toString();
    }

    /**
     * The empty-argument shape of a rewritten call — {@code RATIO_TO_REPORT() OVER ()} — walked as
     * the empty SUM it becomes, so live's "not enough arguments for function [SUM()], expected 1,
     * got 0" is raised at the call.
     */
    private void rejectEmptyRewrittenCall(final FrostlakeParser.FunctionCallExprContext call,
                                          final ExpressionEvaluator checker) {
        final String name = call.functionName().getText();
        if (!"RATIO_TO_REPORT".equalsIgnoreCase(name) || call.functionArgList() != null) {
            return;
        }
        final SourcePosition displacedCall = ExpressionSource.beginNested(
            new SourcePosition(call.getStart().getLine(), call.getStart().getCharPositionInLine()));
        try {
            checker.validateStrictWindowed(ExpressionEvaluator.parse("SUM()"));
        } finally {
            ExpressionSource.end(displacedCall);
        }
    }

    /**
     * The re-formed walk for a window call whose ONLY argument is the bare {@code *}: the star call
     * (over-less) goes through the ordinary strict checks, where the expanded-arity rule refuses the
     * single-argument aggregates and the variadic ones stay legal, positioned at the call.
     */
    private void rejectStarWindowArgument(final FrostlakeParser.FunctionCallExprContext call,
                                          final ExpressionEvaluator checker) {
        if (call.functionArgList() == null || call.functionArgList().functionArg().size() != 1
                || call.functionArgList().functionArg(0).STAR() == null) {
            return;
        }
        final SourcePosition displacedCall = ExpressionSource.beginNested(new SourcePosition(
            call.getStart().getLine(), call.getStart().getCharPositionInLine()));
        try {
            // The star AS WRITTEN — qualifier and filters included — so the re-formed call expands
            // exactly as the plain one would, and refuses exactly as it would.
            checker.validateStrictWindowed(ExpressionEvaluator.parse(call.functionName().getText() + "("
                + ParseTreeText.getOriginalText(call.functionArgList().functionArg(0)) + ")"));
        } finally {
            ExpressionSource.end(displacedCall);
        }
    }

    private void rejectFileWindowKeys(final FrostlakeParser.OverClauseContext overClause, final Table table) {
        final ExpressionEvaluator keyChecker = evaluatorOver(table);
        if (overClause.partitionByClause() != null) {
            for (final FrostlakeParser.ExpressionContext expr
                    : overClause.partitionByClause().expressionList().expression()) {
                keyChecker.validateKey(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(expr)), SortKeyRole.PARTITION_BY);
            }
        }
        if (overClause.orderByClause() != null) {
            for (final FrostlakeParser.OrderItemContext item : overClause.orderByClause().orderItem()) {
                keyChecker.validateKey(
                    ExpressionEvaluator.parse(ParseTreeText.getOriginalText(item.expression())),
                    SortKeyRole.ORDER_BY);
            }
        }
    }

    /**
     * The PARTITION BY key for a row: the value of each PARTITION BY expression (evaluated like the window
     * ORDER BY keys, so a constant is one partition). An empty key (no PARTITION BY) places every row in one
     * partition.
     * List equality is value-by-value, so rows with equal keys group together.
     */
    private List<Object> partitionKey(final Row row, final FrostlakeParser.PartitionByClauseContext partitionBy,
                                      final Table table) {
        final List<Object> key = new ArrayList<>();
        if (partitionBy != null) {
            final CollationSpec[] rules = partitionKeyCollations(partitionBy, table);
            final List<FrostlakeParser.ExpressionContext> keyExprs = partitionBy.expressionList().expression();
            for (int i = 0; i < keyExprs.size(); i++) {
                key.add(CollatedKey.of(ValueComparisons.canonicalGroupKeyValue(
                    evaluateOrderKey(ParseTreeText.getOriginalText(keyExprs.get(i)), row, table)), rules[i]));
            }
        }
        return key;
    }

    /**
     * The collation each PARTITION BY key groups under. Resolved once per clause and held for the window
     * batch, since the key itself is evaluated per row.
     *
     * @param partitionBy the clause
     * @param table       the relation its keys read
     * @return one entry per key, null where the key carries no collation
     */
    private CollationSpec[] partitionKeyCollations(final FrostlakeParser.PartitionByClauseContext partitionBy,
                                                   final Table table) {
        final CollationSpec[] known = rememberedKeyCollations(partitionBy, table);
        if (known != null) {
            return known;
        }
        final List<String> keyTexts = new ArrayList<>();
        for (final FrostlakeParser.ExpressionContext expr : partitionBy.expressionList().expression()) {
            keyTexts.add(ParseTreeText.getOriginalText(expr));
        }
        return rememberKeyCollations(partitionBy, table, keyCollations(keyTexts, table));
    }

    /** A window clause's key collations as already resolved for this relation, or null when not yet. */
    private CollationSpec[] rememberedKeyCollations(final ParserRuleContext clause, final Table table) {
        final Map<ParserRuleContext, CollationSpec[]> forTable = windowKeyRules.get().get(table);
        return forTable == null ? null : forTable.get(clause);
    }

    /** Remember a window clause's key collations for this relation, and hand them back. */
    private CollationSpec[] rememberKeyCollations(final ParserRuleContext clause, final Table table,
                                                  final CollationSpec[] resolved) {
        Map<ParserRuleContext, CollationSpec[]> forTable = windowKeyRules.get().get(table);
        if (forTable == null) {
            forTable = new IdentityHashMap<>();
            windowKeyRules.get().put(table, forTable);
        }
        forTable.put(clause, resolved);
        return resolved;
    }

    /**
     * The collation each window key compares under.
     *
     * @param keyTexts the keys as written
     * @param table    the relation they read
     * @return one entry per key, null where the key carries no collation
     */
    private CollationSpec[] keyCollations(final List<String> keyTexts, final Table table) {
        if (!KeyCollations.reachable(keyTexts, table, null, null)) {
            return new CollationSpec[keyTexts.size()];
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(table,
            executor.getFunctionRegistry(), executor.getCatalog(), executor);
        return KeyCollations.resolve(keyTexts, evaluator);
    }

    // Per-batch, per-thread: the single-pass position/rank maps of each sorted partition, keyed
    // by the partition LIST's identity (partitions are built once per OVER clause and shared).
    // Thread-local because this evaluator is a per-engine singleton and concurrent read-locked
    // SELECTs each run their own batch. Cleared at every batch entry so rows never outlive their
    // query on a pooled thread.
    private final ThreadLocal<Map<List<Row>, WindowPartitionOrder>> partitionOrderCache =
        new ThreadLocal<Map<List<Row>, WindowPartitionOrder>>() {
            @Override
            protected Map<List<Row>, WindowPartitionOrder> initialValue() {
                return new IdentityHashMap<List<Row>, WindowPartitionOrder>();
            }
        };

    // Per-batch, per-thread: each window clause's key collations, under the relation its keys read (one
    // parse tree can serve different relations). The clause is fixed for the batch while its keys are
    // evaluated per row, so resolving the collation once per clause keeps that per-row work off the path.
    private final ThreadLocal<Map<Table, Map<ParserRuleContext, CollationSpec[]>>> windowKeyRules =
        new ThreadLocal<Map<Table, Map<ParserRuleContext, CollationSpec[]>>>() {
            @Override
            protected Map<Table, Map<ParserRuleContext, CollationSpec[]>> initialValue() {
                return new IdentityHashMap<Table, Map<ParserRuleContext, CollationSpec[]>>();
            }
        };

    /** Drop the per-thread partition order maps and argument vectors — called at each window-batch entry. */
    void resetPartitionOrderCache() {
        partitionOrderCache.get().clear();
        frameArgVectors.get().clear();
        argumentFactMemo.get().clear();
        windowKeyRules.get().clear();
    }

    // Per-batch, per-thread: each sorted partition's evaluated ARGUMENT VECTORS, keyed by the
    // partition list's identity and then by the argument's evaluation mode + source text. One
    // evaluation per partition row replaces the former re-evaluation of the same argument for
    // every frame row of every output row — the quadratic expression walks behind a running
    // SUM(x) OVER (ORDER BY …). Cells hold the EXACT objects the row path would produce (no
    // canonicalization); the function-dependent JSON-null mapping stays at read time.
    private final ThreadLocal<Map<List<Row>, Map<String, Object[]>>> frameArgVectors =
        new ThreadLocal<Map<List<Row>, Map<String, Object[]>>>() {
            @Override
            protected Map<List<Row>, Map<String, Object[]>> initialValue() {
                return new IdentityHashMap<List<Row>, Map<String, Object[]>>();
            }
        };

    // Per-batch, per-thread memo of per-ARGUMENT-TEXT facts: "s:" + text → vector-safety verdict,
    // "v:" + text → declared-VARIANT verdict. Both are statement-invariant but were derived per
    // OUTPUT ROW (the variant check re-parsed and re-inferred each time).
    private final ThreadLocal<Map<String, Boolean>> argumentFactMemo =
        new ThreadLocal<Map<String, Boolean>>() {
            @Override
            protected Map<String, Boolean> initialValue() {
                return new HashMap<String, Boolean>();
            }
        };

    /** Argument-vector mode: cells evaluated like ORDER BY keys (a select alias or a grouped value resolves). */
    private static final String VECTOR_MODE_ORDER_KEY = "K:";
    /** Argument-vector mode: cells evaluated as plain expressions (CONDITIONAL_*'s rule). */
    private static final String VECTOR_MODE_EXPRESSION = "E:";

    /** Whether the frame-vector path is on (execution.window.frameVectors, default true). */
    private boolean frameVectorsEnabled() {
        return executor.getEngineConfig() == null
            || executor.getEngineConfig().isWindowFrameVectorsEnabled();
    }

    /**
     * The argument vector to read frames from, or null when the row path must be used — the flag is
     * off, or the argument's shape is not vector-safe. Built on first use per (partition, mode+text)
     * and cached for the batch.
     */
    private Object[] frameArgVector(final List<Row> partition, final String argExpr, final Table table,
                                    final String mode) {
        if (argExpr == null || !frameVectorsEnabled() || !isVectorSafeArgument(argExpr)) {
            return null;
        }
        final Map<List<Row>, Map<String, Object[]>> byPartition = frameArgVectors.get();
        Map<String, Object[]> byArg = byPartition.get(partition);
        if (byArg == null) {
            byArg = new HashMap<>();
            byPartition.put(partition, byArg);
        }
        final String key = mode + argExpr;
        Object[] vector = byArg.get(key);
        if (vector == null) {
            vector = new Object[partition.size()];
            for (int i = 0; i < partition.size(); i++) {
                vector[i] = VECTOR_MODE_ORDER_KEY.equals(mode)
                    ? extractColumnValue(partition.get(i), argExpr, table)
                    : argumentValue(argExpr, partition.get(i), table);
            }
            byArg.put(key, vector);
        }
        return vector;
    }

    /**
     * Whether an argument's parsed shape is safe to evaluate once per row and reuse across frames:
     * literals, column references and semi-structured access, casts, arithmetic/comparison and
     * NULL-tests over those. Everything else — in particular ANY function call, since a volatile
     * one (RANDOM, SEQ…) observably changes value between frame walks — keeps the row path; an
     * unknown node kind therefore fails SAFE. Memoized per argument text for the batch.
     */
    private boolean isVectorSafeArgument(final String argExpr) {
        final Map<String, Boolean> memo = argumentFactMemo.get();
        final String key = "s:" + argExpr;
        final Boolean known = memo.get(key);
        if (known != null) {
            return known;
        }
        boolean safe;
        try {
            safe = isVectorSafeNode(ExpressionEvaluator.parse(argExpr));
        } catch (final RuntimeException notAnExpression) {
            safe = false;
        }
        memo.put(key, safe);
        return safe;
    }

    private boolean isVectorSafeNode(final Expression node) {
        if (node == null) {
            return true;
        }
        if (node instanceof LiteralExpression || node instanceof ColumnReferenceExpression) {
            return true;
        }
        if (node instanceof CastExpression) {
            return isVectorSafeNode(((CastExpression) node).getExpression());
        }
        if (node instanceof UnaryOperationExpression) {
            return isVectorSafeNode(((UnaryOperationExpression) node).getOperand());
        }
        if (node instanceof BinaryOperationExpression) {
            final BinaryOperationExpression bin = (BinaryOperationExpression) node;
            return isVectorSafeNode(bin.getLeft()) && isVectorSafeNode(bin.getRight());
        }
        if (node instanceof IsNullExpression) {
            return isVectorSafeNode(((IsNullExpression) node).getOperand());
        }
        if (node instanceof ObjectAccessExpression) {
            return isVectorSafeNode(((ObjectAccessExpression) node).getBase());
        }
        if (node instanceof ArrayAccessExpression) {
            final ArrayAccessExpression access = (ArrayAccessExpression) node;
            return isVectorSafeNode(access.getArray()) && isVectorSafeNode(access.getIndex());
        }
        return false;
    }

    /**
     * The single-pass position/RANK/DENSE_RANK maps of one sorted partition, built on first use
     * and cached for the batch. One pass replaces the former per-output-row linear scans (with
     * their per-scanned-row ORDER BY key re-evaluation) — the O(n²·keys) that made
     * {@code QUALIFY ROW_NUMBER() OVER (…) = 1} quadratic.
     */
    private WindowPartitionOrder orderMapsFor(final List<Row> sortedRows,
                                              final FrostlakeParser.OverClauseContext overClause,
                                              final Table table) {
        final Map<List<Row>, WindowPartitionOrder> cache = partitionOrderCache.get();
        WindowPartitionOrder order = cache.get(sortedRows);
        if (order != null) {
            return order;
        }
        // IDENTITY-keyed: over grouped rows a projection can make every row EQUAL by value (a select
        // list that is only a window call), and a value-keyed map then held one entry for the whole
        // partition, handing every row the same rank.
        final Map<Row, Long> position = new IdentityHashMap<>();
        final Map<Row, Long> rank = new IdentityHashMap<>();
        final Map<Row, Long> dense = new IdentityHashMap<>();
        final int size = sortedRows.size();
        final int[] firstPeer = new int[size];
        final int[] lastPeer = new int[size];
        long currentRank = 1;
        long currentDense = 1;
        int groupStart = 0;
        List<Object> previousKey = null;
        for (int i = 0; i < size; i++) {
            final Row row = sortedRows.get(i);
            if (overClause.orderByClause() != null) {
                final List<Object> key = orderKeyTuple(row, overClause.orderByClause(), table);
                if (i > 0 && previousKey != null && !orderKeyTuplesEqual(key, previousKey)) {
                    currentRank = i + 1;
                    currentDense++;
                    for (int j = groupStart; j < i; j++) {
                        lastPeer[j] = i - 1;
                    }
                    groupStart = i;
                }
                previousKey = key;
            }
            firstPeer[i] = overClause.orderByClause() != null ? groupStart : 0;
            if (!position.containsKey(row)) {
                position.put(row, Long.valueOf(i + 1));
            }
            if (!rank.containsKey(row)) {
                rank.put(row, Long.valueOf(currentRank));
            }
            if (!dense.containsKey(row)) {
                dense.put(row, Long.valueOf(currentDense));
            }
        }
        for (int j = groupStart; j < size; j++) {
            lastPeer[j] = overClause.orderByClause() != null ? size - 1 : size - 1;
        }
        if (overClause.orderByClause() == null) {
            for (int j = 0; j < size; j++) {
                lastPeer[j] = size - 1;
            }
        }
        order = new WindowPartitionOrder(position, rank, dense, firstPeer, lastPeer);
        cache.put(sortedRows, order);
        return order;
    }

    private Long computeRowNumber(final List<Row> partitionRows, final int currentRowIndex,
                                   final FrostlakeParser.OverClauseContext overClause,
                                   final List<Row> allRows, final Table table) {
        final Row currentRow = allRows.get(currentRowIndex);
        final Long position = orderMapsFor(partitionRows, overClause, table).position(currentRow);
        // Fallback mirrors the former scan: a row absent from its partition keeps its input position.
        return position != null ? position : Long.valueOf(currentRowIndex + 1);
    }

    private Long computeRank(final List<Row> partitionRows, final int currentRowIndex,
                             final FrostlakeParser.OverClauseContext overClause,
                             final List<Row> allRows, final Table table) {
        final Row currentRow = allRows.get(currentRowIndex);
        final Long rank = orderMapsFor(partitionRows, overClause, table).rank(currentRow);
        return rank != null ? rank : Long.valueOf(currentRowIndex + 1);
    }

    private Long computeDenseRank(final List<Row> partitionRows, final int currentRowIndex,
                                   final FrostlakeParser.OverClauseContext overClause,
                                   final List<Row> allRows, final Table table) {
        final Row currentRow = allRows.get(currentRowIndex);
        final Long denseRank = orderMapsFor(partitionRows, overClause, table).denseRank(currentRow);
        return denseRank != null ? denseRank : Long.valueOf(currentRowIndex + 1);
    }

    private Object computeLag(final FrostlakeParser.FunctionCallExprContext funcCtx,
                              final List<Row> partitionRows, final int currentRowIndex,
                              final FrostlakeParser.OverClauseContext overClause,
                              final List<Row> allRows, final Table table) {
        final Row currentRow = allRows.get(currentRowIndex);

        // Parse LAG arguments: LAG(column_expr, offset, default_value)
        // offset defaults to 1, default_value defaults to NULL
        int offset = 1;
        Object defaultValue = null;
        String columnExpr = null;

        if (!windowArgs(funcCtx).isEmpty()) {
            final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);

            // First argument: column expression
            columnExpr = ParseTreeText.getOriginalText(args.get(0));

            // Second argument (optional): offset — evaluated through the expression AST, so a
            // computed constant (1 + 1) is its value, not its source text.
            if (args.size() > 1) {
                final Object offsetVal = constantArgValue(ParseTreeText.getOriginalText(args.get(1)));
                if (offsetVal instanceof Number) {
                    offset = ((Number) offsetVal).intValue();
                } else {
                    logger.warn("Invalid offset for LAG function: {}", args.get(1));
                }
            }

            // Third argument (optional): the default, read against the CURRENT row — a column is a
            // legal default (LAG(a, 1, b) is that row's b on the account), not a constant.
            if (args.size() > 2) {
                defaultValue = defaultArgValue(ParseTreeText.getOriginalText(args.get(2)), currentRow, table);
            }
        }
        final DataType declared = declaredWindowType(funcCtx, table);

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        final List<Row> sortedRows = partitionRows;

        // Find position of current row in sorted partition
        int currentPosition = -1;
        for (int i = 0; i < sortedRows.size(); i++) {
            if (sortedRows.get(i).equals(currentRow)) {
                currentPosition = i;
                break;
            }
        }

        if (currentPosition == -1) {
            return FoldedValues.presented(defaultValue, declared);
        }

        // Calculate the LAG position (backward)
        final int lagPosition = currentPosition - offset;

        // If out of bounds, return default value
        if (lagPosition < 0 || lagPosition >= sortedRows.size()) {
            return FoldedValues.presented(defaultValue, declared);
        }

        // Get the row at LAG position
        final Row lagRow = sortedRows.get(lagPosition);

        // Extract the column value from the LAG row, at the declared fold like the default
        return FoldedValues.presented(extractColumnValue(lagRow, columnExpr, table), declared);
    }

    private Object computeLead(final FrostlakeParser.FunctionCallExprContext funcCtx,
                               final List<Row> partitionRows, final int currentRowIndex,
                               final FrostlakeParser.OverClauseContext overClause,
                               final List<Row> allRows, final Table table) {
        final Row currentRow = allRows.get(currentRowIndex);

        // Parse LEAD arguments: LEAD(column_expr, offset, default_value)
        // offset defaults to 1, default_value defaults to NULL
        int offset = 1;
        Object defaultValue = null;
        String columnExpr = null;

        if (!windowArgs(funcCtx).isEmpty()) {
            final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);

            // First argument: column expression
            columnExpr = ParseTreeText.getOriginalText(args.get(0));

            // Second argument (optional): offset — evaluated through the expression AST, so a
            // computed constant (1 + 1) is its value, not its source text.
            if (args.size() > 1) {
                final Object offsetVal = constantArgValue(ParseTreeText.getOriginalText(args.get(1)));
                if (offsetVal instanceof Number) {
                    offset = ((Number) offsetVal).intValue();
                } else {
                    logger.warn("Invalid offset for LEAD function: {}", args.get(1));
                }
            }

            // Third argument (optional): default value
            if (args.size() > 2) {
                defaultValue = defaultArgValue(ParseTreeText.getOriginalText(args.get(2)), currentRow, table);
            }
        }

        // partitionRows arrives already sorted by the OVER ORDER BY (sorted once per partition by the
        // caller and cached), so no re-sort here.
        final DataType declared = declaredWindowType(funcCtx, table);

        final List<Row> sortedRows = partitionRows;

        // Find position of current row in sorted partition
        int currentPosition = -1;
        for (int i = 0; i < sortedRows.size(); i++) {
            if (sortedRows.get(i).equals(currentRow)) {
                currentPosition = i;
                break;
            }
        }

        if (currentPosition == -1) {
            return FoldedValues.presented(defaultValue, declared);
        }

        // Calculate the LEAD position (forward)
        final int leadPosition = currentPosition + offset;

        // If out of bounds, return default value
        if (leadPosition < 0 || leadPosition >= sortedRows.size()) {
            return FoldedValues.presented(defaultValue, declared);
        }

        // Get the row at LEAD position
        final Row leadRow = sortedRows.get(leadPosition);

        // Extract the column value from the LEAD row
        return FoldedValues.presented(extractColumnValue(leadRow, columnExpr, table), declared);
    }

    /**
     * LAG / LEAD's default, evaluated over the current row so a column reference reads that row; a
     * constant evaluates the same way it always did.
     */
    private Object defaultArgValue(final String argText, final Row currentRow, final Table table) {
        if (argText == null) {
            return null;
        }
        if (table == null) {
            return constantArgValue(argText);
        }
        return executor.evaluateExpression(argText, currentRow, table);
    }

    /**
     * What a window call DECLARES — for LAG / LEAD with a default, the fold of the argument and the
     * default, which both values are presented at. Null when nothing is known.
     */
    private DataType declaredWindowType(final FrostlakeParser.FunctionCallExprContext funcCtx, final Table table) {
        if (table == null) {
            return null;
        }
        try {
            return evaluatorOver(table).inferStaticType(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(funcCtx)));
        } catch (final RuntimeException undetermined) {
            if (SqlCompilationError.isCompilationError(undetermined.getMessage())) {
                throw undetermined;
            }
            return null;
        }
    }

    private Long computeNtile(final FrostlakeParser.FunctionCallExprContext funcCtx,
                               final List<Row> sortedPartition, final Row currentRow,
                               final Table table) {
        int buckets = 1;
        if (!windowArgs(funcCtx).isEmpty()) {
            try { buckets = Integer.parseInt(ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0))); }
            catch (final NumberFormatException ignored) {}
        }
        return WindowFunctionHelper.ntile(sortedPartition, currentRow, buckets);
    }

    private Double computePercentRank(final List<Row> sortedPartition, final Row currentRow,
                                       final FrostlakeParser.OverClauseContext overClause,
                                       final Table table) {
        final List<Object> orderVals = extractOrderValues(sortedPartition, overClause, table);
        return WindowFunctionHelper.percentRank(sortedPartition, currentRow, orderVals);
    }

    private Double computeCumeDist(final List<Row> sortedPartition, final Row currentRow,
                                    final FrostlakeParser.OverClauseContext overClause,
                                    final Table table) {
        final List<Object> orderVals = extractOrderValues(sortedPartition, overClause, table);
        return WindowFunctionHelper.cumeDist(sortedPartition, currentRow, orderVals);
    }

    private Object computeRatioToReport(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                         final List<Row> sortedPartition, final Row currentRow,
                                         final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        final Object curVal = extractColumnValue(currentRow, colExpr, table);
        final List<Object> allVals = frameValues(sortedPartition, 0, sortedPartition.size() - 1, colExpr, table);
        return ratioAtDeclaredScale(funcCtx, WindowFunctionHelper.ratioToReport(curVal, allVals), table);
    }

    /**
     * The ratio presented at the scale its call DECLARES. The quotient is worked out in double
     * arithmetic across the partition, so it arrives with a double's seventeen digits where the call
     * declares eight — or six, or thirty-seven — and live pads or rounds it to that width on a plain
     * read exactly as it does when the value is stored.
     *
     * <p>An INEXACT call is left alone: over a FLOAT, a VARCHAR or a VARIANT argument the ratio IS a
     * double and keeps every digit it has. The scale is read once per call rather than once per row —
     * it is a property of the statement, not of the row.
     *
     * @param funcCtx the window call
     * @param ratio the quotient just computed
     * @param table the relation the call reads
     * @return the ratio at its declared scale, or unchanged when the call is not exact
     */
    private Object ratioAtDeclaredScale(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                        final Object ratio, final Table table) {
        if (!(ratio instanceof Number)) {
            return ratio;
        }
        Integer scale = ratioDeclaredScales.get(funcCtx);
        if (scale == null) {
            scale = Integer.valueOf(declaredRatioScale(funcCtx, table));
            ratioDeclaredScales.put(funcCtx, scale);
        }
        if (scale.intValue() < 0) {
            return ratio;
        }
        return BigDecimal.valueOf(((Number) ratio).doubleValue())
            .setScale(scale.intValue(), RoundingMode.HALF_UP);
    }

    /** The declared scale of one RATIO_TO_REPORT call, or -1 when its type is not an exact number. */
    private int declaredRatioScale(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                   final Table table) {
        final DataType declared;
        try {
            final ExpressionEvaluator types = evaluatorOver(table);
            declared = types.inferStaticType(
                ExpressionEvaluator.parse(ParseTreeText.getOriginalText(funcCtx)));
        } catch (final RuntimeException undetermined) {
            return -1;
        }
        return declared instanceof NumericType && !NumericType.isApproximate(declared)
            ? ((NumericType) declared).getScale() : -1;
    }

    private Object computeFirstValue(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                      final List<Row> partition, final int from, final int to, final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        return WindowFunctionHelper.firstValue(
            frameValues(partition, from, to, colExpr, table), ignoreNulls(funcCtx));
    }

    /** The argument's values over frame rows {@code [from, to]} — vector reads when safe, else row evaluation. */
    private List<Object> frameValues(final List<Row> partition, final int from, final int to,
                                     final String colExpr, final Table table) {
        final Object[] vector = frameArgVector(partition, colExpr, table, VECTOR_MODE_ORDER_KEY);
        final List<Object> values = new ArrayList<>(Math.max(0, to - from + 1));
        for (int i = from; i <= to; i++) {
            values.add(vector != null ? vector[i] : extractColumnValue(partition.get(i), colExpr, table));
        }
        return values;
    }

    private Object computeLastValue(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                     final List<Row> partition, final int from, final int to, final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        return WindowFunctionHelper.lastValue(
            frameValues(partition, from, to, colExpr, table), ignoreNulls(funcCtx));
    }

    /** Whether a window value function carries an explicit {@code IGNORE NULLS} clause (default RESPECT).
     *  The clause may sit inside the argument parens or between the call and OVER — both grammar
     *  positions land in the same list (at most one is present in a valid call). */
    private boolean ignoreNulls(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        for (final FrostlakeParser.NullHandlingContext nullHandling : funcCtx.nullHandling()) {
            if (nullHandling.IGNORE() != null) {
                return true;
            }
        }
        return false;
    }

    private Object computeNthValue(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                    final List<Row> partition, final int from, final int to, final Table table) {
        if (windowArgs(funcCtx).size() < 2) return null;
        final String colExpr = ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0));
        int n = 1;
        try { n = Integer.parseInt(ParseTreeText.getOriginalText(windowArgs(funcCtx).get(1))); }
        catch (final NumberFormatException ignored) {}
        List<Object> vals = frameValues(partition, from, to, colExpr, table);
        if (ignoreNulls(funcCtx)) {
            final List<Object> nonNull = new ArrayList<>();
            for (final Object v : vals) {
                if (v != null) {
                    nonNull.add(v);
                }
            }
            vals = nonNull;
        }
        if (funcCtx.LAST() != null) {
            // NTH_VALUE(x, n) FROM LAST: the n-th value counting backwards from the partition end.
            Collections.reverse(vals);
        }
        return WindowFunctionHelper.nthValue(vals, n);
    }

    /**
     * The window frame slice (a contiguous sub-list of the sorted partition) for the current row, per the
     * OVER clause's frame. With no explicit frame the default is the whole partition when there is no ORDER
     * BY, otherwise {@code RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW} — a running, peer-aware window
     * (Snowflake's default). ROWS bounds are positional; RANGE supports UNBOUNDED and CURRENT ROW
     * (peer-aware). Frame-sensitive functions (SUM/AVG/MIN/MAX/COUNT, FIRST_VALUE/LAST_VALUE/NTH_VALUE) use
     * this; ranking and LAG/LEAD ignore the frame.
     */
    /**
     * The frame for the VALUE window functions (FIRST_VALUE / LAST_VALUE / NTH_VALUE), whose
     * Snowflake defaults differ from the aggregates: ORDER BY is REQUIRED ("Window function type
     * [NTH_VALUE] requires ORDER BY in window specification."), and with no explicit frame the
     * default is the WHOLE partition — LAST_VALUE(x) OVER (ORDER BY y) is the partition's last
     * value on every row (both live-verified). An explicit frame is honoured normally.
     */
    private int[] valueFrameBounds(final String functionName,
                                   final FrostlakeParser.OverClauseContext overClause,
                                   final List<Row> partition, final Row currentRow, final Table table) {
        if (overClause.orderByClause() == null) {
            throw new RuntimeException("Window function type [" + functionName
                + "] requires ORDER BY in window specification.");
        }
        if (overClause.windowFrame() == null) {
            return new int[] {0, partition.size() - 1};
        }
        return frameBounds(overClause, partition, currentRow, table);
    }

    private List<Row> frameRows(final FrostlakeParser.OverClauseContext overClause, final List<Row> partition,
                                final Row currentRow, final Table table) {
        final int[] bounds = frameBounds(overClause, partition, currentRow, table);
        if (bounds[0] > bounds[1]) {
            return new ArrayList<>();   // an empty frame (e.g. 2 FOLLOWING at the end of the partition)
        }
        return partition.subList(bounds[0], bounds[1] + 1);
    }

    /**
     * The frame as inclusive {@code [start, end]} indices into the sorted partition (start &gt; end
     * means an empty frame). This is {@link #frameRows}' bound computation without the subList, so
     * vector readers can address the partition's argument vector directly.
     */
    private int[] frameBounds(final FrostlakeParser.OverClauseContext overClause, final List<Row> partition,
                              final Row currentRow, final Table table) {
        final int size = partition.size();
        if (size == 0) {
            return new int[] {0, -1};
        }
        // Anchor via the partition's single-pass position map (first-equal, exactly what the old
        // linear scan found), falling back to the last index as before.
        final Long anchor = orderMapsFor(partition, overClause, table).position(currentRow);
        final int pos = anchor != null ? (int) (anchor.longValue() - 1) : size - 1;
        final FrostlakeParser.WindowFrameContext frame = overClause.windowFrame();
        final boolean hasOrderBy = overClause.orderByClause() != null;

        if (frame == null) {
            if (!hasOrderBy) {
                return new int[] {0, size - 1};   // no ORDER BY ⇒ the frame is the whole partition
            }
            // Default running frame: UNBOUNDED PRECEDING … CURRENT ROW (RANGE ⇒ through the last peer).
            return new int[] {0, lastPeer(partition, pos, overClause, table)};
        }

        final boolean isRange = frame.RANGE() != null;
        final List<FrostlakeParser.FrameBoundContext> bounds = frame.frameBound();
        final int start = boundIndex(partition, pos, bounds.get(0), overClause, table, isRange, true);
        final int end = bounds.size() > 1
            ? boundIndex(partition, pos, bounds.get(1), overClause, table, isRange, false)
            : (isRange ? lastPeer(partition, pos, overClause, table) : pos);   // single bound ⇒ … AND CURRENT ROW

        return new int[] {Math.max(0, start), Math.min(size - 1, end)};
    }

    /** Resolve one frame bound to an inclusive index into the sorted partition. ROWS bounds are positional;
     *  RANGE bounds support UNBOUNDED and CURRENT ROW (peer-aware) — a RANGE numeric offset is rejected. */
    private int boundIndex(final List<Row> partition, final int pos, final FrostlakeParser.FrameBoundContext bound,
                           final FrostlakeParser.OverClauseContext overClause, final Table table,
                           final boolean isRange, final boolean isStart) {
        if (bound.UNBOUNDED() != null) {
            return bound.PRECEDING() != null ? 0 : partition.size() - 1;
        }
        if (bound.CURRENT() != null) {   // CURRENT ROW
            if (!isRange) {
                return pos;
            }
            return isStart ? firstPeer(partition, pos, overClause, table)
                           : lastPeer(partition, pos, overClause, table);
        }
        // expression PRECEDING | expression FOLLOWING
        if (isRange) {
            return rangeBoundIndex(partition, pos, bound, overClause, table, isStart);
        }
        final int n = parseFrameOffset(bound.expression());
        return bound.PRECEDING() != null ? pos - n : pos + n;
    }

    /**
     * Resolve a RANGE numeric-offset bound to an index: the frame is VALUE-based, so include rows whose
     * (single, numeric) ORDER BY value is within {@code offset} of the current row's value on the
     * PRECEDING/FOLLOWING side. Handles ASC and DESC. A START bound returns the first included index; an END
     * bound the last (both scan the already-sorted partition). Requires a single numeric ORDER BY column.
     */
    private int rangeBoundIndex(final List<Row> partition, final int pos, final FrostlakeParser.FrameBoundContext bound,
                                final FrostlakeParser.OverClauseContext overClause, final Table table, final boolean isStart) {
        final Object curVal = getOrderByValue(partition.get(pos), overClause.orderByClause(), table);
        if (!(curVal instanceof Number)) {
            throw new RuntimeException("RANGE frame with a numeric offset requires a single numeric ORDER BY column");
        }
        final double offset;
        try {
            offset = Double.parseDouble(ParseTreeText.getOriginalText(bound.expression()).trim());
        } catch (final NumberFormatException e) {
            throw new RuntimeException("RANGE frame offset must be a numeric constant: "
                + ParseTreeText.getOriginalText(bound.expression()));
        }
        final double v = ((Number) curVal).doubleValue();
        final boolean asc = overClause.orderByClause().orderItem().get(0).DESC() == null;
        final boolean preceding = bound.PRECEDING() != null;
        final double threshold = preceding ? (asc ? v - offset : v + offset) : (asc ? v + offset : v - offset);
        if (isStart) {
            for (int i = 0; i < partition.size(); i++) {
                if (rangeValueAtOrBeyond(partition.get(i), overClause, table, threshold, asc, true)) {
                    return i;
                }
            }
            return partition.size();   // nothing qualifies ⇒ empty frame (start > end)
        }
        int last = -1;
        for (int i = 0; i < partition.size(); i++) {
            if (rangeValueAtOrBeyond(partition.get(i), overClause, table, threshold, asc, false)) {
                last = i;
            }
        }
        return last;
    }

    /** Whether a row's ORDER BY value is on the included side of {@code threshold} for a RANGE start/end bound. */
    private boolean rangeValueAtOrBeyond(final Row row, final FrostlakeParser.OverClauseContext overClause,
                                         final Table table, final double threshold, final boolean asc, final boolean isStart) {
        final Object o = getOrderByValue(row, overClause.orderByClause(), table);
        if (!(o instanceof Number)) {
            return false;
        }
        final double x = ((Number) o).doubleValue();
        // START keeps values on the far side of the lower edge; END keeps values up to the upper edge.
        if (isStart) {
            return asc ? x >= threshold : x <= threshold;
        }
        return asc ? x <= threshold : x >= threshold;
    }

    private int parseFrameOffset(final FrostlakeParser.ExpressionContext expr) {
        try {
            return Integer.parseInt(ParseTreeText.getOriginalText(expr).trim());
        } catch (final NumberFormatException e) {
            throw new RuntimeException("window frame offset must be a non-negative integer: " + ParseTreeText.getOriginalText(expr));
        }
    }

    /** First index whose ORDER BY value equals the current row's (its first peer); 0 when there is no ORDER BY.
     *  An array read off the partition's single-pass order maps. */
    private int firstPeer(final List<Row> partition, final int pos, final FrostlakeParser.OverClauseContext overClause,
                          final Table table) {
        if (overClause.orderByClause() == null) {
            return 0;
        }
        return orderMapsFor(partition, overClause, table).firstPeer(pos);
    }

    /** Last index whose ORDER BY value equals the current row's (its last peer); the last index when there
     *  is no ORDER BY. An array read off the partition's single-pass order maps. */
    private int lastPeer(final List<Row> partition, final int pos, final FrostlakeParser.OverClauseContext overClause,
                         final Table table) {
        if (overClause.orderByClause() == null) {
            return partition.size() - 1;
        }
        return orderMapsFor(partition, overClause, table).lastPeer(pos);
    }

    /**
     * Window aggregates SUM/AVG/MIN/MAX over the partition. Computed over the whole partition (Snowflake's
     * default frame when there is no ORDER BY), matching this engine's COUNT and RATIO_TO_REPORT window
     * behaviour; running/cumulative frames (ORDER BY … ROWS/RANGE) are not yet modelled here.
     */
    /**
     * Whether this window is CUMULATIVE — an ORDER BY carrying no ROWS frame. A RANGE frame stays
     * cumulative and a ROWS frame does not, which is the frame KEYWORD deciding it rather than the span
     * the frame covers (live-verified; the same rule types the call in TypeInferencer).
     */
    private boolean isCumulativeWindow(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        if (funcCtx == null || funcCtx.overClause() == null) {
            return false;
        }
        final FrostlakeParser.OverClauseContext over = funcCtx.overClause();
        return over.orderByClause() != null
            && (over.windowFrame() == null || over.windowFrame().ROWS() == null);
    }

    private Object computeWindowAggregate(final String functionName, final FrostlakeParser.FunctionCallExprContext funcCtx,
                                          final List<Row> partition, final int from, final int to, final Table table) {
        final String colExpr = !windowArgs(funcCtx).isEmpty()
            ? ParseTreeText.getOriginalText(windowArgs(funcCtx).get(0)) : null;
        final Object[] vector = frameArgVector(partition, colExpr, table, VECTOR_MODE_ORDER_KEY);
        final List<Object> values = new ArrayList<>(Math.max(0, to - from + 1));
        for (int i = from; i <= to; i++) {
            // Same JSON-null rule as the grouped path and the generic window dispatch: a VARIANT
            // JSON null is missing input for SUM/AVG and a VALUE for MIN/MAX — without this a
            // windowed AVG counted the JSON null as 0 in its divisor (grouped AVG skips it).
            values.add(colExpr == null ? null
                : VariantJsonNulls.asAggregateInput(functionName, vector != null ? vector[i]
                    : extractColumnValue(partition.get(i), colExpr, table)));
        }
        switch (functionName) {
            // A FLOAT sum is read by the frame's shape: the whole partition as one corrected sum, a
            // cumulative RANGE frame a peer group at a time, every other frame row by row
            // (live-verified; see PartialFloatSum).
            case "SUM": return WindowFunctionHelper.sum(framePartials(funcCtx, values, partition, from, to, table),
                isStaticallyVariantArgumentMemo(colExpr, table), isRowWiseFrame(funcCtx));
            case "AVG":
                // The window's SHAPE decides the scale: a CUMULATIVE window (an ORDER BY with no ROWS
                // frame — a RANGE frame is still cumulative) averages at the aggregate's own width,
                // and every other shape three decimals narrower, TRUNCATED. Live-verified.
                // …and an APPROXIMATE argument keeps the double path whichever shape it is, for the
                // same reason a VARIANT one does: there is no exact scale to widen. A FLOAT-declared
                // value can still arrive in an exact carrier, so its declared type is what says so.
                // The FLOAT sum under it is read by the frame's shape, as SUM's is.
                final boolean avgKeepsDouble = isStaticallyVariantArgumentMemo(colExpr, table)
                    || isApproximateColumn(colExpr, table);
                return WindowFunctionHelper.avg(framePartials(funcCtx, values, partition, from, to, table),
                    avgKeepsDouble, !isCumulativeWindow(funcCtx), isRowWiseFrame(funcCtx));
            case "MIN": return WindowFunctionHelper.min(values);
            case "MAX": return WindowFunctionHelper.max(values);
            default:    return null;
        }
    }

    /**
     * Whether a SUM or AVG frame reads its FLOAT sum row by row, the running sum's last compensation left
     * unapplied: every frame but the whole partition and a cumulative RANGE frame — a ROWS frame short of
     * UNBOUNDED at both ends, a RANGE frame of the current row's peers alone. Live, over 0.3 then 0.7
     * squared, {@code OVER (ORDER BY i ROWS UNBOUNDED PRECEDING)} answers 0.57999999999999996003 on its
     * last row where {@code OVER ()} answers 0.57999999999999984901.
     */
    private static boolean isRowWiseFrame(final FrostlakeParser.FunctionCallExprContext funcCtx) {
        if (funcCtx == null || funcCtx.overClause() == null) {
            return false;
        }
        final FrostlakeParser.OverClauseContext over = funcCtx.overClause();
        return !isWholePartitionFrame(over) && !isPeerCumulativeFrame(over);
    }

    /** No ORDER BY and no frame, or a frame UNBOUNDED at both ends: the whole partition by its definition. */
    private static boolean isWholePartitionFrame(final FrostlakeParser.OverClauseContext over) {
        final FrostlakeParser.WindowFrameContext frame = over.windowFrame();
        if (frame == null) {
            return over.orderByClause() == null;
        }
        final List<FrostlakeParser.FrameBoundContext> bounds = frame.frameBound();
        return bounds.size() == 2
            && bounds.get(0).UNBOUNDED() != null && bounds.get(0).PRECEDING() != null
            && bounds.get(1).UNBOUNDED() != null && bounds.get(1).FOLLOWING() != null;
    }

    /**
     * A cumulative RANGE frame — an ORDER BY with no frame, or RANGE from UNBOUNDED PRECEDING to the
     * CURRENT ROW or from the CURRENT ROW to UNBOUNDED FOLLOWING — which live adds a PEER GROUP at a time:
     * each group's FLOAT sum corrected, the groups summed as a running sum. Over rows that all share the
     * ORDER BY key it answers the corrected 0.57999999999999984901 where the same rows under ROWS
     * UNBOUNDED PRECEDING answer 0.57999999999999996003.
     */
    private static boolean isPeerCumulativeFrame(final FrostlakeParser.OverClauseContext over) {
        final FrostlakeParser.WindowFrameContext frame = over.windowFrame();
        if (frame == null) {
            return over.orderByClause() != null;
        }
        if (frame.RANGE() == null) {
            return false;
        }
        final List<FrostlakeParser.FrameBoundContext> bounds = frame.frameBound();
        final FrostlakeParser.FrameBoundContext start = bounds.get(0);
        final boolean startsUnbounded = start.UNBOUNDED() != null && start.PRECEDING() != null;
        if (bounds.size() == 1) {
            return startsUnbounded;
        }
        final FrostlakeParser.FrameBoundContext end = bounds.get(1);
        return (startsUnbounded && end.CURRENT() != null)
            || (start.CURRENT() != null && end.UNBOUNDED() != null && end.FOLLOWING() != null);
    }

    /**
     * A SUM or AVG frame's values as the partials its FLOAT sum is read in: one per peer group for a
     * cumulative RANGE frame, the whole frame as one otherwise (a row-wise frame splits it itself).
     */
    private List<List<Object>> framePartials(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                             final List<Object> values, final List<Row> partition,
                                             final int from, final int to, final Table table) {
        if (funcCtx == null || funcCtx.overClause() == null || !isPeerCumulativeFrame(funcCtx.overClause())) {
            return Collections.singletonList(values);
        }
        final List<List<Object>> groups = new ArrayList<>();
        int start = from;
        while (start <= to) {
            final int end = Math.max(start, Math.min(to, lastPeer(partition, start, funcCtx.overClause(), table)));
            groups.add(values.subList(start - from, end - from + 1));
            start = end + 1;
        }
        return groups;
    }

    /** Per-batch memo over {@link #isStaticallyVariantArgument} — the verdict is per argument TEXT,
     *  but was re-derived (a parse plus a static-type walk) for every output row. */
    private boolean isStaticallyVariantArgumentMemo(final String argExpr, final Table table) {
        if (argExpr == null || argExpr.isEmpty()) {
            return false;
        }
        final Map<String, Boolean> memo = argumentFactMemo.get();
        final String key = "v:" + argExpr;
        final Boolean known = memo.get(key);
        if (known != null) {
            return known;
        }
        final boolean variant = isStaticallyVariantArgument(argExpr, table);
        memo.put(key, variant);
        return variant;
    }

    /**
     * Whether the window aggregate's ARGUMENT is declared VARIANT — live, {@code SUM(v:b) OVER ()}
     * and {@code AVG(v:b) OVER ()} are DOUBLE (SYSTEM$TYPEOF FLOAT) exactly like the grouped forms,
     * even though path extraction hands the engine plain integers; the declared type decides the
     * tier just as a runtime VariantValue does.
     */
    private boolean isStaticallyVariantArgument(final String argExpr, final Table table) {
        if (argExpr == null || argExpr.isEmpty()) {
            return false;
        }
        try {
            final ExpressionEvaluator ev = evaluatorOver(table);
            return ev.inferStaticType(ExpressionEvaluator.parse(argExpr)) instanceof VariantType;
        } catch (final RuntimeException undetermined) {
            return false;
        }
    }

    /**
     * COUNT over the frame. With an argument, only rows whose argument value is non-null count — and
     * a VARIANT JSON null is missing input exactly as on the grouped path (live: {@code COUNT(v:b)
     * OVER ()} is 0 over a lone JSON-null row, where this method used to answer the frame size — it
     * ignored its argument entirely, so SQL NULLs were miscounted too). A star call does not reach
     * this method, so an empty argument list only defends against a malformed call.
     */
    private Object computeWindowCount(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                      final List<Row> partition, final int from, final int to, final Table table) {
        final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);
        if (args.isEmpty()) {
            final StarArgument star = StarArgument.ofWindowCall(funcCtx);
            if (star == null || star.isBare() || table == null) {
                return (long) Math.max(0, to - from + 1);
            }
            // A qualified or filtered star is a column list, and COUNT over a list counts the frame
            // rows in which EVERY column is non-NULL (live-verified: COUNT(t.*) OVER () is 1 over
            // (1, 2, 3), (4, NULL, 6), (NULL, NULL, NULL) where COUNT(*) OVER () is 3).
            final List<String> columns = expandWindowStar(star, table);
            long count = 0;
            for (int i = from; i <= to; i++) {
                boolean anyNull = false;
                for (final String column : columns) {
                    if (VariantJsonNulls.asAggregateInput("COUNT",
                            extractColumnValue(partition.get(i), column, table)) == null) {
                        anyNull = true;
                        break;
                    }
                }
                if (!anyNull) {
                    count++;
                }
            }
            return count;
        }
        final List<String> listed = windowArgTexts(funcCtx, table);
        if (listed.size() > 1) {
            // COUNT over a LIST counts the frame rows in which every value is present, a star beside
            // other arguments spliced in as its columns (live: COUNT(a, t.*) OVER () is 1 over (1, 2, 3),
            // (4, NULL, 6), (NULL, NULL, NULL)).
            long count = 0;
            for (int i = from; i <= to; i++) {
                boolean anyNull = false;
                for (final String column : listed) {
                    if (VariantJsonNulls.asAggregateInput("COUNT",
                            extractColumnValue(partition.get(i), column, table)) == null) {
                        anyNull = true;
                        break;
                    }
                }
                if (!anyNull) {
                    count++;
                }
            }
            return count;
        }
        final String argExpr = ParseTreeText.getOriginalText(args.get(0));
        final Object[] vector = frameArgVector(partition, argExpr, table, VECTOR_MODE_ORDER_KEY);
        long count = 0;
        for (int i = from; i <= to; i++) {
            final Object raw = vector != null ? vector[i] : extractColumnValue(partition.get(i), argExpr, table);
            if (VariantJsonNulls.asAggregateInput("COUNT", raw) != null) {
                count++;
            }
        }
        return count;
    }

    /** A window call's argument texts, a star beside other arguments spliced in place as its columns. */
    private List<String> windowArgTexts(final FrostlakeParser.FunctionCallExprContext funcCtx, final Table table) {
        final List<String> texts = new ArrayList<>();
        if (funcCtx.functionArgList() != null) {
            for (final FrostlakeParser.FunctionArgContext arg : funcCtx.functionArgList().functionArg()) {
                final StarArgument star = StarArgument.of(arg);
                if (star != null) {
                    texts.addAll(expandWindowStar(star, table));
                } else if (arg.booleanExpr() != null) {
                    texts.add(ParseTreeText.getOriginalText(arg.booleanExpr()));
                }
            }
        }
        return texts;
    }

    /**
     * A registered aggregate applied as a window function over the current row's frame: each frame row's
     * argument value is fed to a fresh accumulator (DISTINCT drops repeats), mirroring the grouped path.
     */
    private Object computeGenericWindowAggregate(final AggregateFunction aggFunc,
                                                 final FrostlakeParser.FunctionCallExprContext funcCtx,
                                                 final List<Row> frame, final Table table) {
        final List<FrostlakeParser.BooleanExprContext> args = windowArgs(funcCtx);
        final String secondExpr = args.size() > 1 ? ParseTreeText.getOriginalText(args.get(1)) : null;
        final AggregateFunction.Accumulator acc = aggFunc.createAccumulator();
        // ★ A WITHIN GROUP call's VALUES come from that clause's key, never from argument one — which
        // for a percentile is the FRACTION. Feeding the arguments blindly handed the accumulator 0.5
        // once per row, so the answer was the fraction itself for every row of every partition.
        final FrostlakeParser.OrderByClauseContext withinGroup =
            AggregateFunctions.withinGroupOrderBy(funcCtx);
        final boolean orderedPercentile = withinGroup != null && !withinGroup.orderItem().isEmpty()
            && (acc instanceof PercentileContAccumulator || acc instanceof PercentileDiscAccumulator);
        if (orderedPercentile) {
            final double fraction = args.isEmpty() ? 0.5d
                : new BigDecimal(ParseTreeText.getOriginalText(args.get(0)).trim()).doubleValue();
            if (acc instanceof PercentileContAccumulator) {
                ((PercentileContAccumulator) acc).setPercentile(fraction);
            } else {
                ((PercentileDiscAccumulator) acc).setPercentile(fraction);
            }
        }
        final String argExpr = orderedPercentile
            ? ParseTreeText.getOriginalText(withinGroup.orderItem(0).expression())
            : (!args.isEmpty() ? ParseTreeText.getOriginalText(args.get(0)) : null);
        if (acc instanceof ApproximateAwareAccumulator) {
            ((ApproximateAwareAccumulator) acc).setApproximateArgument(
                isApproximateColumn(argExpr, table));
        }
        if (acc instanceof CoercedNumericArgumentAccumulator) {
            ((CoercedNumericArgumentAccumulator) acc).setCoercedNumericArgument(
                isCoercedNumericColumn(argExpr, table));
        }
        if (acc instanceof DeclaredArgumentAccumulator) {
            ((DeclaredArgumentAccumulator) acc).setDeclaredArgumentType(declaredArgumentType(argExpr, table));
        }
        if (acc instanceof ConstantArgumentsAccumulator) {
            ((ConstantArgumentsAccumulator) acc).setConstantArgumentTexts(tupleArgumentTexts(funcCtx, args, table));
        }
        // Two-argument aggregates mirror the grouped path's dispatch: LISTAGG / APPROX_PERCENTILE take
        // their constant second argument up front; the pair-fed accumulators (MAX_BY / MIN_BY,
        // OBJECT_AGG, CORR / COVAR / REGR) receive both per-row values. Feeding only the first argument
        // silently returned an empty/NULL aggregate over the frame.
        if (secondExpr != null && acc instanceof ListAggAccumulator) {
            ((ListAggAccumulator) acc).setDelimiter(String.valueOf(constantArgValue(secondExpr)));
        } else if (secondExpr != null && acc instanceof ApproxPercentileAccumulator) {
            ((ApproxPercentileAccumulator) acc).setPercentile(new BigDecimal(secondExpr.trim()).doubleValue());
        }
        if (acc instanceof MultiArgumentAccumulator) {
            // The tuple-fed seam, as on the grouped path: an accumulator that wants every argument gets
            // the whole row, in the order written. Feeding it only argument one made a windowed
            // HASH_AGG(a, b) equal to HASH_AGG(a), where live folds both.
            accumulateWindowTuples((MultiArgumentAccumulator) acc, aggFunc.getName(),
                tupleArgumentTexts(funcCtx, args, table), frame, table, funcCtx.DISTINCT() != null);
            return acc.getResult();
        }
        final boolean pairFed = secondExpr != null
            && (acc instanceof MaxByMinByAccumulator || acc instanceof ObjectAggAccumulator
                || acc instanceof CorrAccumulator || acc instanceof CovarAccumulator
                || acc instanceof RegrAccumulator);
        final Set<Object> seen = funcCtx.DISTINCT() != null ? new HashSet<>() : null;
        for (final Row r : frame) {
            // Same JSON-null rule as the grouped path: a VARIANT JSON null is missing input for a
            // value-computing aggregate (live: COUNT ignores it, SUM over {JSON null, 3}
            // is 3) and a VALUE for an ordering/collecting one (MAX over {JSON null, 5} is the JSON
            // null). Without this a windowed COUNT(v:b) counted rows a grouped COUNT(v:b) skipped.
            final Object v = argExpr == null ? null
                : VariantJsonNulls.asAggregateInput(aggFunc.getName(), extractColumnValue(r, argExpr, table));
            if (seen != null && v != null && !seen.add(ValueComparisons.normalizeValueForDistinct(v))) {
                continue;
            }
            if (pairFed) {
                final Object second = extractColumnValue(r, secondExpr, table);
                if (acc instanceof MaxByMinByAccumulator) {
                    ((MaxByMinByAccumulator) acc).accumulate(v, second);
                } else if (acc instanceof ObjectAggAccumulator) {
                    ((ObjectAggAccumulator) acc).accumulate(v, second);
                } else if (v != null && second != null) {
                    final double dy = WindowFunctionHelper.toDouble(v);
                    final double dx = WindowFunctionHelper.toDouble(second);
                    if (acc instanceof CorrAccumulator) {
                        ((CorrAccumulator) acc).accumulate(dy, dx);
                    } else if (acc instanceof CovarAccumulator) {
                        ((CovarAccumulator) acc).accumulate(dy, dx);
                    } else {
                        ((RegrAccumulator) acc).accumulate(dy, dx);
                    }
                }
            } else {
                acc.accumulate(v);
            }
        }
        return acc.getResult();
    }

    /**
     * Every frame row's whole argument tuple, fed to an accumulator that asked for it. DISTINCT compares
     * the TUPLE, and a tuple holding a NULL never repeats — the single-argument rule this generalises.
     *
     * @param acc the tuple-fed accumulator
     * @param aggregateName the aggregate's name, for the VARIANT JSON-null reading rule
     * @param argumentTexts the call's argument texts, in the order written or expanded from a star
     * @param frame the rows this output row's window covers
     * @param table the row layout the argument texts resolve against
     * @param distinct whether the call wrote DISTINCT
     */
    private void accumulateWindowTuples(final MultiArgumentAccumulator acc, final String aggregateName,
                                        final List<String> argumentTexts,
                                        final List<Row> frame, final Table table,
                                        final boolean distinct) {
        final Set<Object> seenTuples = distinct ? new HashSet<>() : null;
        for (final Row r : frame) {
            final List<Object> tuple = new ArrayList<>(argumentTexts.size());
            boolean holdsNull = false;
            for (final String argument : argumentTexts) {
                final Object value = VariantJsonNulls.asAggregateInput(aggregateName,
                    extractColumnValue(r, argument, table));
                holdsNull = holdsNull || value == null;
                tuple.add(value);
            }
            if (seenTuples != null && !holdsNull) {
                final List<Object> key = new ArrayList<>(tuple.size());
                for (final Object value : tuple) {
                    key.add(ValueComparisons.normalizeValueForDistinct(value));
                }
                if (!seenTuples.add(key)) {
                    continue;
                }
            }
            acc.accumulate(tuple);
        }
    }

    /**
     * The argument texts a tuple-fed window aggregate reads, with a bare {@code *} EXPANDED to the
     * in-scope column list — the same expansion the plain aggregate applies, so
     * {@code HASH_AGG(*) OVER ()} answers exactly what {@code HASH_AGG(a, b, c) OVER ()} answers
     * (live-verified: the two agree). A star is not a boolean expression, so it reaches the window
     * stage as no argument at all, and the call used to hash an empty tuple per row instead.
     *
     * @param funcCtx the window call
     * @param args its boolean-expression arguments, empty for a star
     * @param table the row layout the star expands over
     * @return the argument texts, in the order written or the columns' order
     */
    private List<String> tupleArgumentTexts(final FrostlakeParser.FunctionCallExprContext funcCtx,
                                                   final List<FrostlakeParser.BooleanExprContext> args,
                                                   final Table table) {
        final StarArgument star = args.isEmpty() ? StarArgument.ofWindowCall(funcCtx) : null;
        if (star != null && table != null) {
            return expandWindowStar(star, table);
        }
        final List<String> texts = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext argument : args) {
            texts.add(ParseTreeText.getOriginalText(argument));
        }
        return texts;
    }

    /**
     * A window call's star expanded over the row layout — with its qualifier, EXCLUDE and ILIKE
     * honoured, and the joined relations consulted when the query has them.
     *
     * @param star the star argument
     * @param table the row layout
     * @return the column texts the star stands for
     */
    private List<String> expandWindowStar(final StarArgument star, final Table table) {
        final Map<String, Table> aliases = groupedValues == null ? null : groupedValues.aliasToTable();
        final List<Table> relations = groupedValues == null ? null : groupedValues.allTables();
        return star.expand(table, aliases, relations, relations != null && !relations.isEmpty());
    }

    private List<Object> extractOrderValues(final List<Row> sortedRows,
                                             final FrostlakeParser.OverClauseContext overClause,
                                             final Table table) {
        final List<Object> vals = new ArrayList<>();
        for (final Row r : sortedRows) {
            vals.add(overClause.orderByClause() != null ? getOrderByValue(r, overClause.orderByClause(), table) : null);
        }
        return vals;
    }

    /**
     * Whether an aggregated expression NAMES a column declared FLOAT / DOUBLE / REAL. Anything that is
     * not a plain column answers false — an expression has no declared column to read, and the column
     * lookup REFUSES a name it cannot find rather than returning nothing, so it is asked only where an
     * answer is possible.
     */
    /**
     * The declared type of a window aggregate's argument, for the accumulators that report it —
     * an expression's static type, or null when it cannot be typed.
     */
    private DataType declaredArgumentType(final String expressionText, final Table table) {
        if (expressionText == null || table == null) {
            return null;
        }
        try {
            return evaluatorOver(table).inferStaticType(ExpressionEvaluator.parse(expressionText));
        } catch (final RuntimeException undetermined) {
            return null;
        }
    }

    private boolean isApproximateColumn(final String expressionText, final Table table) {
        if (expressionText == null || table == null) {
            return false;
        }
        for (final TableColumn column : table.getColumns()) {
            if (column.getName().equalsIgnoreCase(expressionText.trim())) {
                return NumericType.isApproximate(column.getDataType());
            }
        }
        return false;
    }

    /**
     * Whether a window aggregate's argument is a plain column declared VARCHAR or VARIANT — the family
     * whose values a percentile converts to whole numbers, see {@link CoercedNumericArgumentAccumulator}.
     * Same reach as {@link #isApproximateColumn}: a plain column answers, an expression answers false.
     */
    private boolean isCoercedNumericColumn(final String expressionText, final Table table) {
        if (expressionText == null || table == null) {
            return false;
        }
        for (final TableColumn column : table.getColumns()) {
            if (column.getName().equalsIgnoreCase(expressionText.trim())) {
                final DataType declared = column.getDataType();
                return declared instanceof StringType || declared instanceof VariantType;
            }
        }
        return false;
    }

    private Object extractColumnValue(final Row row, final String columnExpr, final Table table) {
        if (columnExpr == null || table == null) {
            return null;
        }
        // Same resolution as ORDER BY keys: the evaluated expression — so LAG / LEAD / FIRST_VALUE /
        // NTH_VALUE accept qualified names and casts (e.g. LAG(t.value::VARCHAR)), not just bare column
        // names, and a number is the constant it spells (LAG(1) lags the constant 1).
        return evaluateOrderKey(columnExpr, row, table);
    }

    /**
     * Evaluate a CONSTANT window-function argument (offset, default, delimiter) through the
     * expression AST. Hand-stripping quotes off the flattened source text returned escaped quotes
     * undecoded ({@code 'O''Brien'} stayed {@code O''Brien}) and computed constants like
     * {@code 1 + 1} as their own source text.
     */
    private Object constantArgValue(final String argText) {
        if (argText == null) {
            return null;
        }
        return executor.evaluateExpression(argText, null, (Table) null);
    }

    List<Row> sortRowsForWindow(final List<Row> rows, final FrostlakeParser.OrderByClauseContext orderByClause, final Table table) {
        final List<Row> sorted = new ArrayList<>(rows);
        if (sorted.size() <= 1) {
            return sorted;
        }

        // Direction/nulls flags and expression texts are row-independent — resolve them ONCE.
        final List<FrostlakeParser.OrderItemContext> items = orderByClause.orderItem();
        final boolean[] ascending = new boolean[items.size()];
        final Boolean[] nullsFirst = new Boolean[items.size()];
        final String[] exprTexts = new String[items.size()];
        for (int i = 0; i < items.size(); i++) {
            ascending[i] = items.get(i).DESC() == null;
            nullsFirst[i] = ValueComparisons.nullsFirstFlag(items.get(i));
            exprTexts[i] = ParseTreeText.getOriginalText(items.get(i).expression());
        }
        // A key that carries a collation sorts under it — the same rule the statement's own ORDER BY
        // follows, applied to a window's ordering and to a WITHIN GROUP one.
        final CollationSpec[] keyRules = keyCollations(Arrays.asList(exprTexts), table);

        // Evaluate each row's ORDER BY keys ONCE (so qualified names, casts, and expressions all resolve —
        // not just bare column names), then compare the cached tuples; the sort runs once per partition, so
        // re-evaluating on every comparison would be O(rows^2 log rows).
        final Map<Row, Object[]> keyCache = new IdentityHashMap<>();
        for (final Row r : sorted) {
            final Object[] keys = new Object[items.size()];
            for (int i = 0; i < items.size(); i++) {
                keys[i] = CollatedKey.of(evaluateOrderKey(exprTexts[i], r, table), keyRules[i]);
            }
            keyCache.put(r, keys);
        }

        sorted.sort(new Comparator<Row>() {
            @Override
            public int compare(final Row row1, final Row row2) {
                final Object[] k1 = keyCache.get(row1);
                final Object[] k2 = keyCache.get(row2);
                for (int i = 0; i < exprTexts.length; i++) {
                    final int cmp = ValueComparisons.compareOrderKey(k1[i], k2[i], ascending[i], nullsFirst[i]);
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        return sorted;
    }

    /**
     * Evaluate one window ORDER BY / PARTITION BY key, or a window argument, for a row. The expression is
     * EVALUATED — so qualified names ({@code t.value}), casts ({@code t.value::VARCHAR}), and arbitrary
     * expressions resolve, not only bare column names. A number is the constant it spells, never a
     * position in the select list: that reading belongs to the query's own ORDER BY and GROUP BY, while
     * inside OVER and WITHIN GROUP a constant key is one partition, or a tie between every row
     * (live-verified). Returns null when it cannot be evaluated.
     */
    private Object evaluateOrderKey(final String exprText, final Row row, final Table table) {
        final String trimmed = exprText.trim();
        // Grouped (projected) window stage: a key that IS one of the SELECT items — typically a raw
        // aggregate, OVER (ORDER BY SUM(amount) DESC) — reads that item's already-computed value
        // positionally. Evaluating the aggregate text as a scalar threw, and the catch below turned
        // the key into NULL, so ROW_NUMBER/RANK ordered every partition by input order instead.
        if (!windowSelectItemCanonicalIndex.isEmpty()) {
            try {
                final Integer itemIdx = windowSelectItemCanonicalIndex.get(
                    AstPrinterVisitor.print(ExpressionEvaluator.parse(trimmed)));
                if (itemIdx != null && itemIdx < row.getValues().size()) {
                    return row.getValue(itemIdx);
                }
            } catch (final RuntimeException notAnExpression) {
                // fall through to the alias / generic paths
            }
        }
        // Still grouped, and the key is NOT one of the select items — the group itself is the only
        // place its value exists. This is the ordinary shape: OVER (ORDER BY SUM(b)) beside a SELECT
        // list that never projects SUM(b).
        final Object grouped = groupedValueOf(trimmed, row);
        if (grouped != GroupedExpressionValues.UNRESOLVED) {
            return grouped;
        }
        // A bare name that names a SELECT-list alias (and isn't a base column) resolves to the alias's
        // defining expression — Snowflake allows a window PARTITION BY / ORDER BY to reference a SELECT
        // alias. A real column of the same name still takes precedence. Aliases CHAIN (a priority CASE
        // defined over two sibling aliases), so the defining expression is expanded transitively —
        // evaluating it raw threw "Column not found" per row, which the catch below silently turned
        // into a NULL order key, so RANK/ROW_NUMBER saw every row as equal and QUALIFY filtered nothing.
        String toEvaluate = trimmed;
        if (!windowSelectAliases.isEmpty() && isSimpleIdentifier(trimmed)) {
            final String canon = canonicalName(trimmed);
            if (!table.hasColumn(canon) && windowSelectAliases.containsKey(canon)) {
                toEvaluate = expandAliasExpression(windowSelectAliases.get(canon), table);
            }
        }
        try {
            return executor.evaluateExpression(toEvaluate, row, table);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Transitively inline sibling select-alias references inside an alias's defining expression, so a
     * chained alias evaluates against the source row. Lexer-driven substitution keeps qualified-name
     * and {@code :path} segments untouched (an alias named like a variant path key must not explode),
     * and a real column of the same name is never substituted. Bounded passes guard cycles.
     */
    private String expandAliasExpression(final String defining, final Table table) {
        String text = defining;
        for (int pass = 0; pass < 5; pass++) {
            String next = text;
            for (final Map.Entry<String, String> alias : windowSelectAliases.entrySet()) {
                if (table != null && table.hasColumn(alias.getKey())) {
                    continue;
                }
                next = SqlIdentifierSubstitution.substitute(next, alias.getKey(), "(" + alias.getValue() + ")");
            }
            if (next.equals(text)) {
                break;
            }
            text = next;
        }
        return text;
    }

    /** True if the text is a single unqualified identifier (a bare column/alias name, possibly quoted) —
     *  i.e. a candidate for SELECT-alias resolution, unlike {@code t.col}, a cast, or an expression. */
    private static boolean isSimpleIdentifier(final String s) {
        final int n = s.length();
        if (n == 0) {
            return false;
        }
        if (n >= 2 && s.charAt(0) == '"' && s.charAt(n - 1) == '"') {
            return true;   // quoted identifier
        }
        if (Character.isDigit(s.charAt(0))) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            final char c = s.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '$')) {
                return false;
            }
        }
        return true;
    }

    /** Canonicalize a bare identifier the same way {@link SqlIdentifiers#canonical}: strip and preserve the
     *  case of a quoted name; upper-case an unquoted one. */
    private static String canonicalName(final String raw) {
        final String t = raw.trim();
        if (t.length() >= 2 && t.charAt(0) == '"' && t.charAt(t.length() - 1) == '"') {
            return t.substring(1, t.length() - 1);
        }
        return t.toUpperCase();
    }

    /** All ORDER BY key values for a row, in order — used for peer/tie detection across EVERY key. */
    private List<Object> orderKeyTuple(final Row row, final FrostlakeParser.OrderByClauseContext orderByClause,
                                       final Table table) {
        final List<String> keyTexts = new ArrayList<>();
        for (final FrostlakeParser.OrderItemContext item : orderByClause.orderItem()) {
            keyTexts.add(ParseTreeText.getOriginalText(item.expression()));
        }
        // Peers are decided by the same rules the sort ran under, so two values a collation calls equal
        // rank together — RANK and DENSE_RANK would otherwise number them apart in a sorted partition.
        final CollationSpec[] remembered = rememberedKeyCollations(orderByClause, table);
        final CollationSpec[] keyRules = remembered != null ? remembered
            : rememberKeyCollations(orderByClause, table, keyCollations(keyTexts, table));
        final List<Object> keys = new ArrayList<>();
        for (int i = 0; i < keyTexts.size(); i++) {
            keys.add(CollatedKey.of(evaluateOrderKey(keyTexts.get(i), row, table), keyRules[i]));
        }
        return keys;
    }

    /** Two ORDER BY tuples are peers when every key compares equal (NULLs peer with NULLs). */
    private boolean orderKeyTuplesEqual(final List<Object> a, final List<Object> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!compareOrderValues(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    /** The first ORDER BY key's value for a row — used by RANGE frames, which require a single numeric key. */
    private Object getOrderByValue(final Row row, final FrostlakeParser.OrderByClauseContext orderByClause, final Table table) {
        if (orderByClause.orderItem().isEmpty()) {
            return null;
        }
        return evaluateOrderKey(ParseTreeText.getOriginalText(orderByClause.orderItem().get(0).expression()), row, table);
    }

    private boolean compareOrderValues(final Object val1, final Object val2) {
        if (val1 == null && val2 == null) return true;
        if (val1 == null || val2 == null) return false;

        // Use numeric comparison for numbers
        if (val1 instanceof Number && val2 instanceof Number) {
            return Double.compare(((Number) val1).doubleValue(), ((Number) val2).doubleValue()) == 0;
        }

        return val1.equals(val2);
    }
}
