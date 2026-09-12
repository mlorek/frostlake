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

package dev.frostlake.executor.expressions;

import java.util.List;

/**
 * A window function call (one carrying an {@code OVER} clause) that appears nested inside a larger
 * expression — e.g. {@code revenue - LAG(revenue) OVER (ORDER BY yr)} in a SELECT item, or an inline
 * {@code ROW_NUMBER() OVER (...)} in a QUALIFY predicate.
 *
 * <p>Window functions are not evaluated per row by {@link ExpressionEvaluatorVisitor}; they are computed
 * over a whole partition by the window stage. This node lets a window call keep its place in the
 * expression AST (instead of being string-substituted out and re-parsed): the window stage precomputes
 * each call's per-row value and supplies it through the evaluator's result context, keyed by the call's
 * exact source text ({@link #getCallText()}). The evaluator resolves this node to that value.
 *
 * <p>Carrying the call as a node — rather than swapping its text for a synthetic name and re-parsing —
 * keeps the AST the single source of truth about the expression's structure.
 */
public class WindowFunctionExpression implements Expression {

    private final String callText;
    private SourcePosition position;
    private Expression withinGroupOrdered;
    private String functionName;
    private List<Expression> arguments;
    /** The star-shaped call a windowed star argument stands for ({@code COUNT(t.* EXCLUDE (a)) OVER ()}), or null. */
    private FunctionCallExpression starCall;
    private boolean ordered;
    private boolean rowsFramed;
    private boolean distinct;
    /** The OVER clause's keys, or null when the call was not described that far (a star call). */
    private List<Expression> partitionKeys;
    private List<Expression> orderKeys;
    private List<Boolean> orderAscending;
    private List<Boolean> orderNullsFirst;

    public WindowFunctionExpression(final String callText) {
        this.callText = callText;
    }

    /** The window call's exact source text; the key under which its per-row value is supplied. */
    public String getCallText() {
        return callText;
    }

    /** Where the call begins in its source, so a refusal can point at it. */
    public SourcePosition getPosition() {
        return position;
    }

    public void setPosition(final SourcePosition position) {
        this.position = position;
    }

    /**
     * Record what the call IS, read off the parse tree when this node was built. Evaluation never uses
     * it — the value still arrives by call text — but the static channel needs the name and arguments
     * to type the window functions that hand their argument back, and re-parsing the text to recover
     * them would make the text the source of truth again.
     *
     * @param name      the function's name, upper-cased
     * @param callArgs  its argument expressions
     */
    public void describeCall(final String name, final List<Expression> callArgs) {
        this.functionName = name;
        this.arguments = callArgs;
    }

    /**
     * Record the SHAPE of the OVER clause, read off the same parse tree. AVG declares a different width
     * over a window that carries a bare ORDER BY than over one that does not, and an explicit frame puts
     * it back — a distinction nothing but these two flags can make, and one the call text must not be
     * re-read to recover.
     *
     * @param hasOrderBy      whether the OVER clause carries an ORDER BY
     * @param hasRowsFrame whether it carries a ROWS frame of its own — a RANGE frame does NOT
     *     count, because live treats a RANGE-framed window as CUMULATIVE for width purposes
     */
    public void describeWindow(final boolean hasOrderBy, final boolean hasRowsFrame) {
        this.ordered = hasOrderBy;
        this.rowsFramed = hasRowsFrame;
    }

    /** Whether the OVER clause carries an ORDER BY. */
    public boolean isOrdered() {
        return ordered;
    }

    /**
     * Describe the OVER clause itself, so a message can re-print the call the way live's plan spells
     * it — every key qualified, every sort key with its direction and null placement, the frame and
     * the null treatment dropped — rather than as the source text it was written in.
     *
     * @param distinctCall whether the call wrote DISTINCT
     * @param partition the PARTITION BY keys, in order (empty when none)
     * @param order the ORDER BY keys, in order (empty when none)
     * @param ascending per sort key, whether it is ascending
     * @param nullsFirst per sort key, the explicit NULLS FIRST (TRUE) / LAST (FALSE), or null when unspecified
     */
    public void describeOver(final boolean distinctCall, final List<Expression> partition,
                             final List<Expression> order, final List<Boolean> ascending,
                             final List<Boolean> nullsFirst) {
        this.distinct = distinctCall;
        this.partitionKeys = partition;
        this.orderKeys = order;
        this.orderAscending = ascending;
        this.orderNullsFirst = nullsFirst;
    }

    public boolean isDistinct() {
        return distinct;
    }

    /** The PARTITION BY keys, or null when the OVER clause was never described. */
    public List<Expression> getPartitionKeys() {
        return partitionKeys;
    }

    public List<Expression> getOrderKeys() {
        return orderKeys;
    }

    public List<Boolean> getOrderAscending() {
        return orderAscending;
    }

    public List<Boolean> getOrderNullsFirst() {
        return orderNullsFirst;
    }

    /**
     * Whether the OVER clause carries a ROWS frame of its own.
     *
     * <p>A RANGE frame deliberately does NOT count: live-verified, {@code OVER (ORDER BY y RANGE
     * BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)} declares the same width as a bare ORDER BY, while
     * the ROWS spelling of the same span narrows it. The distinction is the FRAME KEYWORD, not what
     * the frame actually spans.
     */
    public boolean isRowsFramed() {
        return rowsFramed;
    }

    /** The window function's name, or null when this node was built without one. */
    public String getFunctionName() {
        return functionName;
    }

    /** The window call's argument expressions, or null when this node was built without them. */
    /**
     * The star this window call's sole argument is — carrying its qualifier, EXCLUDE and ILIKE — as a
     * star-shaped call the arity walk expands exactly as it expands the plain star call; null when the
     * argument list is not a lone star.
     */
    public FunctionCallExpression getStarCall() {
        return starCall;
    }

    public void describeStar(final FunctionCallExpression star) {
        this.starCall = star;
    }

    public List<Expression> getArguments() {
        return arguments;
    }

    /**
     * Record the call's {@code WITHIN GROUP (ORDER BY …)} key, which is where an ORDERED aggregate's
     * VALUES come from — for a percentile the arguments carry only the fraction, so a node without this
     * has nothing to type from but the fraction, and typed the fraction.
     *
     * @param ordered the WITHIN GROUP ORDER BY expression, or null when the call has no such clause
     */
    public void describeWithinGroup(final Expression ordered) {
        this.withinGroupOrdered = ordered;
    }

    /** The {@code WITHIN GROUP (ORDER BY …)} key, or null when the call carries no such clause. */
    public Expression getWithinGroupOrdered() {
        return withinGroupOrdered;
    }

    @Override
    public <T> T accept(final ExpressionVisitor<T> visitor) {
        return visitor.visitWindowFunction(this);
    }
}
