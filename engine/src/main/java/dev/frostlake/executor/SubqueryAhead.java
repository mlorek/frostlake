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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;

import org.antlr.v4.runtime.ParserRuleContext;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The faults a scalar subquery raises ahead of the rows that read its value. Live computes an uncorrelated
 * scalar subquery that reads a relation as a part of the plan of its own, once, before the query around it
 * produces a row: what that computation raises — its own row faults, and the fault of its returning more
 * than one row — is raised whether or not a row of the query around it survives to read the value.
 * {@code SELECT v, (SELECT v FROM t) AS x FROM t GROUP BY v HAVING v = 5} is "Single-row subquery returns
 * more than one row." and {@code (SELECT v/0 FROM t WHERE v = 1)} in its place is "Division by zero", where
 * {@code SELECT v, 1/0 FROM t GROUP BY v HAVING v = 5} answers no row (live-verified).
 *
 * <p>Two computations are folded into the expression that holds the subquery instead, and wait for a row as
 * that expression's own faults do: a subquery without a FROM, {@code (SELECT 1/0)}, and what a subquery of one
 * implicit group computes above its aggregates, {@code (SELECT MAX(v)/0 FROM t)} — while its aggregates' own
 * faults, {@code (SELECT SUM(v/0) FROM t)}, are raised ahead (live-verified). A subquery that reads the row
 * around it is computed for that row, and its faults wait with the row.
 *
 * <p>A fault is marked by identity, as {@link DeferredFault} marks the faults its cells hold, so it travels
 * unchanged to whatever decides whether it may wait in a cell.
 */
public final class SubqueryAhead {

    /** The faults raised ahead of the rows, by identity. */
    private static final Map<Throwable, Boolean> AHEAD =
        Collections.synchronizedMap(new WeakHashMap<Throwable, Boolean>());

    /** The faults a grouped query handed out of its own result cells, by identity. */
    private static final Map<Throwable, Boolean> OUTPUT_CELLS =
        Collections.synchronizedMap(new WeakHashMap<Throwable, Boolean>());

    private SubqueryAhead() {
    }

    /**
     * Marks the fault a scalar subquery raised when that subquery is one live computes ahead of the rows.
     *
     * @param executor     the executor the subquery's text is read with
     * @param subquery     the subquery's text
     * @param fault        what computing it raised
     * @param uncorrelated whether the computation read no value of the row around it
     * @return the fault, marked or not
     */
    public static RuntimeException raised(final QueryExecutor executor, final String subquery,
                                          final RuntimeException fault, final boolean uncorrelated) {
        if (uncorrelated && executor != null && computedAhead(executor.subqueryStatement(subquery), fault)) {
            AHEAD.put(fault, Boolean.TRUE);
        }
        return fault;
    }

    /**
     * Whether a failure is a fault a subquery raised ahead of the rows.
     *
     * @param failure the failure caught
     * @return whether it may not wait in a cell
     */
    public static boolean isAhead(final Throwable failure) {
        return failure != null && AHEAD.containsKey(failure);
    }

    /**
     * Whether a failure an item's evaluation raised may wait in the item's cell for a reader: a row fault (see
     * {@link DeferredFault#deferrable}) that no subquery raised ahead of the rows.
     *
     * @param failure the failure
     * @return whether it waits
     */
    public static boolean waits(final RuntimeException failure) {
        return DeferredFault.deferrable(failure) && !isAhead(failure);
    }

    /**
     * Computes the scalar subqueries a select list computes whatever its rows hold (see {@link AheadSubqueryWalk})
     * and that live computes ahead of the rows, raising what they raise ahead; anything else they raise is left
     * for the row that reads them. A plan that settles its rows before computing its items — a HAVING that is
     * a constant — runs this first, as live's plan computes such a subquery before any row.
     *
     * @param executor   the executor the subqueries' texts are read with
     * @param selectList the select list
     * @param evaluator  an evaluator over the query's relation
     * @param row        a row of that relation, or one of NULLs where there is none
     */
    static void computeAhead(final QueryExecutor executor, final FrostlakeParser.SelectListContext selectList,
                             final ExpressionEvaluator evaluator, final Row row) {
        for (final FrostlakeParser.SelectItemContext item : selectList.selectItem()) {
            final ParserRuleContext expression = SelectItemAccessors.getItemExpression(item);
            if (expression == null) {
                continue;
            }
            final Expression parsed;
            try {
                parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(expression));
            } catch (final RuntimeException unparsed) {
                continue;
            }
            final AheadSubqueryWalk walk = new AheadSubqueryWalk();
            parsed.accept(walk);
            for (final SubqueryExpression subquery : walk.subqueries()) {
                if (!readsARelation(executor.subqueryStatement(subquery.getSubquery()))) {
                    continue;
                }
                try {
                    evaluator.evaluate(subquery, row);
                } catch (final RuntimeException raised) {
                    if (isAhead(raised)) {
                        throw raised;
                    }
                }
            }
        }
    }

    /**
     * Notes that a grouped query handed a fault out of one of its own result cells: the fault of what it
     * computes above its aggregates.
     *
     * @param fault the fault
     * @return the same fault
     */
    static RuntimeException fromOutputCell(final RuntimeException fault) {
        OUTPUT_CELLS.put(fault, Boolean.TRUE);
        return fault;
    }

    /**
     * Whether a subquery of this shape raised this fault ahead of the rows: anything but a select without a
     * FROM, and anything but the fault of what one implicit group computes above its aggregates.
     */
    private static boolean computedAhead(final FrostlakeParser.SelectStatementContext statement,
                                         final RuntimeException fault) {
        if (!readsARelation(statement)) {
            return false;
        }
        final FrostlakeParser.SelectClauseContext select = statement.setOperator().isEmpty()
            ? statement.selectOperand(0).selectClause() : null;
        return !(select != null && OUTPUT_CELLS.containsKey(fault) && oneImplicitGroup(statement, select));
    }

    /** Whether a subquery is a plan of its own: a set operation, or a select that reads a relation. */
    private static boolean readsARelation(final FrostlakeParser.SelectStatementContext statement) {
        if (statement == null) {
            return false;
        }
        if (!statement.setOperator().isEmpty()) {
            return true;
        }
        final FrostlakeParser.SelectClauseContext select = statement.selectOperand(0).selectClause();
        return select != null && select.tableExpression() != null;
    }

    /** Whether a select makes exactly one group of whatever it reads: no GROUP BY, and nothing that drops it. */
    private static boolean oneImplicitGroup(final FrostlakeParser.SelectStatementContext statement,
                                            final FrostlakeParser.SelectClauseContext select) {
        return select.groupByClause() == null && select.havingClause() == null && select.qualifyClause() == null
            && select.topClause() == null && statement.limitClause() == null && statement.fetchClause() == null;
    }
}
