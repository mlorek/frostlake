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

import dev.frostlake.executor.expressions.ColumnReferenceCollectWalk;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;

import java.math.BigInteger;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Whether a scalar subquery an UPDATE or a MERGE writes can answer NULL, as the account types the value it writes
 * (live-verified). A subquery that reads a table, a view or any other source of rows can find none, and answers NULL
 * then whatever its item is, a NOT NULL column included; so can one whose item reads a name of the statement around
 * it, {@code (SELECT n * 12345)}. One that reads neither answers as its single item is judged (see
 * {@link ResultNullability}), a column of a derived table of no source as the item that made it, under a WHERE TRUE
 * or a LIMIT of one row or more alike: {@code (SELECT 12345)}, {@code (SELECT 12345 LIMIT 1)} and
 * {@code (SELECT v FROM (SELECT 12345 v))} answer no NULL, where {@code (SELECT NULLIF(12345, 0))},
 * {@code (SELECT MAX(12345))} and {@code (SELECT v FROM (SELECT NULLIF(12345, 0) v))} can. A subquery of any other
 * shape can.
 */
final class ScalarSubqueryNullability {

    /** The judgements made, by subquery text: a MERGE judges its values row by row. */
    private static final Map<String, Boolean> JUDGED = new ConcurrentHashMap<String, Boolean>();

    /** How many judgements are kept before they are dropped. */
    private static final int KEPT = 512;

    private static final ResultNullability ITEMS = new ResultNullability(null, null);

    private ScalarSubqueryNullability() {
    }

    /**
     * Whether the subquery can answer NULL.
     *
     * @param executor the executor that parses the subquery
     * @param subquery the subquery
     * @return whether it can
     */
    static boolean of(final QueryExecutor executor, final SubqueryExpression subquery) {
        final String text = subquery.getSubquery();
        final Boolean judged = JUDGED.get(text);
        if (judged != null) {
            return judged.booleanValue();
        }
        boolean nullable;
        try {
            nullable = of(executor.parseSelectStatement(text), null);
        } catch (final RuntimeException unreadable) {
            nullable = true;
        }
        if (JUDGED.size() >= KEPT) {
            JUDGED.clear();
        }
        JUDGED.put(text, Boolean.valueOf(nullable));
        return nullable;
    }

    /**
     * Whether a query of no source answers NULL in its item named {@code name}, its single item when the name is
     * null; true for a query that reads a source or that is of another shape.
     */
    private static boolean of(final FrostlakeParser.SelectStatementContext statement, final String name) {
        if (statement.withClause() != null || statement.orderByClause() != null || statement.fetchClause() != null
                || !oneRowOrMore(statement.limitClause()) || statement.selectOperand().size() != 1
                || statement.selectOperand(0).selectClause() == null) {
            return true;
        }
        final FrostlakeParser.SelectClauseContext clause = statement.selectOperand(0).selectClause();
        if (clause.groupByClause() != null || clause.havingClause() != null || clause.qualifyClause() != null
                || clause.connectByClause() != null || clause.topClause() != null
                || !alwaysTrue(clause.whereClause())) {
            return true;
        }
        final Expression item = item(clause, name);
        if (item == null) {
            return true;
        }
        final FrostlakeParser.TableExpressionContext from = clause.tableExpression();
        if (from == null) {
            final ColumnReferenceCollectWalk names = new ColumnReferenceCollectWalk();
            item.accept(names);
            return !names.references().isEmpty() || ITEMS.of(item);
        }
        if (from.tableReference().size() != 1 || !from.joinClause().isEmpty()
                || !(item instanceof ColumnReferenceExpression)) {
            return true;
        }
        final FrostlakeParser.TableReferenceContext reference = from.tableReference(0);
        if (reference.LATERAL() != null || reference.identifierList() != null || reference.pivotClause() != null
                || reference.unpivotClause() != null || reference.matchRecognizeClause() != null
                || reference.sampleClause() != null || reference.tableSource().selectStatement() == null) {
            return true;
        }
        return of(reference.tableSource().selectStatement(), ((ColumnReferenceExpression) item).getColumnName());
    }

    /** Whether a LIMIT keeps the one row there is: none written, or a count of one or more with no OFFSET. */
    private static boolean oneRowOrMore(final FrostlakeParser.LimitClauseContext limit) {
        if (limit == null) {
            return true;
        }
        if (limit.OFFSET() != null || limit.INTEGER_LITERAL().size() != 1) {
            return false;
        }
        return new BigInteger(limit.INTEGER_LITERAL(0).getText()).signum() > 0;
    }

    /** Whether a WHERE keeps every row: none written, or the literal TRUE. */
    private static boolean alwaysTrue(final FrostlakeParser.WhereClauseContext where) {
        if (where == null) {
            return true;
        }
        final Expression condition = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(where.booleanExpr()));
        return condition instanceof LiteralExpression
            && ((LiteralExpression) condition).getType() == LiteralType.BOOLEAN
            && Boolean.TRUE.equals(((LiteralExpression) condition).getValue());
    }

    /** The item of a select list named {@code name}, or its only item when the name is null; null when none is. */
    private static Expression item(final FrostlakeParser.SelectClauseContext clause, final String name) {
        if (name == null && clause.selectList().selectItem().size() != 1) {
            return null;
        }
        for (final FrostlakeParser.SelectItemContext selectItem : clause.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(selectItem)) {
                return null;
            }
            final String alias = SelectItemAccessors.getItemAlias(selectItem);
            if (name == null || alias != null && alias.equalsIgnoreCase(name)) {
                final FrostlakeParser.ExpressionContext value = SelectItemAccessors.getItemValueExpr(selectItem);
                final ParserRuleContext text = value != null ? value
                    : SelectItemAccessors.getItemExpression(selectItem);
                return text == null ? null : ExpressionEvaluator.parse(ParseTreeText.getOriginalText(text));
            }
        }
        return null;
    }
}
