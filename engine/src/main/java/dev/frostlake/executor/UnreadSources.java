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
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the thread is planning a query that reads no row of its FROM clause, whose table functions are therefore
 * never called (live-verified): a WHERE with a constant conjunct that is FALSE or NULL — {@code WHERE 1 = 0},
 * {@code WHERE FALSE}, {@code WHERE NULL}, {@code WHERE 1 = 0 AND name = 'x'}, but not {@code WHERE 1 = 0 OR
 * name = 'x'} — a LIMIT 0, and an inner join whose condition is such a constant. A call whose arguments it would
 * refuse, or whose input it could not read, answers no row and no refusal then. A query planned inside one that
 * reads no row reads none either.
 */
final class UnreadSources {

    private static final ThreadLocal<Boolean> IN_FORCE = new ThreadLocal<Boolean>();

    private UnreadSources() {
    }

    /**
     * Note the query about to be planned.
     *
     * @param readsNoRow whether it reads no row of its own FROM (see {@link #readsNoRow})
     * @return whether a query around it read none, for {@link #end}
     */
    static boolean begin(final boolean readsNoRow) {
        final boolean outer = inForce();
        if (outer || readsNoRow) {
            IN_FORCE.set(Boolean.TRUE);
        }
        return outer;
    }

    /**
     * Put back what {@link #begin} found.
     *
     * @param outer what it returned
     */
    static void end(final boolean outer) {
        if (outer) {
            IN_FORCE.set(Boolean.TRUE);
        } else {
            IN_FORCE.remove();
        }
    }

    /**
     * Whether the query being planned reads no row of its FROM.
     *
     * @return true while one that reads none is planned
     */
    static boolean inForce() {
        return Boolean.TRUE.equals(IN_FORCE.get());
    }

    /**
     * Whether a query block reads no row of its FROM: its WHERE or an inner join's condition holds a constant conjunct
     * that is FALSE or NULL, or the statement it is the one query of is limited to no row.
     *
     * @param executor  the executor, which evaluates a constant
     * @param statement the statement the block belongs to, or null
     * @param clause    the block
     * @return whether it reads no row
     */
    static boolean readsNoRow(final QueryExecutor executor, final FrostlakeParser.SelectStatementContext statement,
                              final FrostlakeParser.SelectClauseContext clause) {
        if (clause == null || clause.tableExpression() == null) {
            return false;
        }
        if (statement != null && statement.selectOperand().size() == 1 && statement.limitClause() != null
                && statement.limitClause().getChildCount() == 2
                && "0".equals(statement.limitClause().getChild(1).getText())) {
            return true;
        }
        final FrostlakeParser.WhereClauseContext where = ParseTreeText.getWhereClause(clause);
        if (where != null && holdsNothingConstant(executor, where.booleanExpr())) {
            return true;
        }
        for (final FrostlakeParser.JoinClauseContext join : clause.tableExpression().joinClause()) {
            final boolean inner = join.CROSS() == null && join.ASOF() == null
                && (join.joinType() == null || join.joinType().INNER() != null);
            if (inner && join.ON() != null && holdsNothingConstant(executor, join.booleanExpr())) {
                return true;
            }
        }
        return false;
    }

    /** Whether a condition's top-level conjuncts hold a constant one that is FALSE or NULL. */
    private static boolean holdsNothingConstant(final QueryExecutor executor,
                                                final FrostlakeParser.BooleanExprContext condition) {
        final List<FrostlakeParser.BooleanExprContext> conjuncts = new ArrayList<>();
        collectConjuncts(condition, conjuncts);
        for (final FrostlakeParser.BooleanExprContext conjunct : conjuncts) {
            final Expression folded;
            try {
                folded = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(conjunct));
            } catch (final RuntimeException unreadable) {
                continue;
            }
            if (!isConstant(folded)) {
                continue;
            }
            try {
                final Object value = new ExpressionEvaluator(new Table("DUMMY", new ArrayList<>(), false),
                    executor.getFunctionRegistry(), executor.getCatalog(), executor)
                    .evaluate(folded, new Row(new Object[0]));
                if (value == null || Boolean.FALSE.equals(value)) {
                    return true;
                }
            } catch (final RuntimeException faulting) {
                // A constant that faults is left to the evaluation, which reports it.
            }
        }
        return false;
    }

    private static void collectConjuncts(final FrostlakeParser.BooleanExprContext condition,
                                         final List<FrostlakeParser.BooleanExprContext> into) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            for (final FrostlakeParser.BooleanExprContext operand
                    : ((FrostlakeParser.AndExprContext) condition).booleanExpr()) {
                collectConjuncts(operand, into);
            }
            return;
        }
        into.add(condition);
    }

    /** Whether an expression is a literal, or casts, signs and operators over literals alone. */
    private static boolean isConstant(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof CastExpression) {
            return isConstant(((CastExpression) expr).getExpression());
        }
        if (expr instanceof UnaryOperationExpression) {
            return isConstant(((UnaryOperationExpression) expr).getOperand());
        }
        if (expr instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) expr;
            return isConstant(binary.getLeft()) && isConstant(binary.getRight())
                && (binary.getEscape() == null || isConstant(binary.getEscape()));
        }
        return false;
    }
}
