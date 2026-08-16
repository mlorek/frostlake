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

import dev.frostlake.executor.expressions.BetweenExpression;
import dev.frostlake.executor.expressions.BinaryOperationExpression;
import dev.frostlake.executor.expressions.BinaryOperator;
import dev.frostlake.executor.expressions.CastExpression;
import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.IsNullExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.SubqueryExpression;
import dev.frostlake.executor.expressions.UnaryOperationExpression;
import dev.frostlake.executor.expressions.UnaryOperator;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import dev.frostlake.values.RelationStatistics;
import dev.frostlake.values.ValueRange;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Whether a SELECT clause reads within its table's statistics. The account keeps a row count for a table
 * and, per column, the least and the greatest value and whether any is NULL. While every row of the one
 * table a query reads reaches its aggregates, it bounds a COUNT by that row count (the storage tag
 * {@code SYSTEM$TYPEOF} prints) and answers a COUNT, a MIN or a MAX from the statistics alone.
 *
 * <p>A join, a grouping key or a HAVING takes the query past that bound, and so does a WHERE unless the
 * statistics prove it true of every row: a constant, a comparison of a NUMBER, string or date and time
 * column with a constant, a comparison of exact-number expressions the columns' intervals settle over
 * columns holding no NULL ({@code c + 1 > 0}, {@code ABS(c) > 0}), BETWEEN, IS [NOT] NULL, a column
 * against itself, and NOT, AND and OR over those. A scalar subquery the planner folds is a constant too
 * (see {@link ConstantSubqueries}). An IN list is never proven, even one naming every value, and a column
 * holding a NULL proves no comparison. Past the bound a COUNT is tagged by its declared width, and a
 * typeof over COUNT, MIN or MAX folds to the scan like any other aggregate. Every rule is live-verified.
 *
 * <p>A derived relation — a subquery, a CTE, a view — is inlined when it projects one table's rows under a
 * WHERE the statistics prove (see {@link RelationStatistics}): a query over it is bounded exactly as a
 * query over the table is, and proven over the relation's own rows. Any other relation that is no catalog
 * table — VALUES, a table function, a derived relation that groups, orders, limits, unites or joins —
 * reads past the bound. The decision is taken on first use: only a typeof asks, so no other query reads
 * the table for it.
 */
final class CountStatisticsBound {

    private final QueryExecutor executor;
    private final FrostlakeParser.SelectClauseContext ctx;
    private final Table table;
    private final Map<String, Table> aliasToTable;
    private final String whereExpr;
    private final boolean joined;
    private final List<Row> sourceRows;
    private Boolean unbounded;
    private boolean deciding;
    private ExpressionEvaluator evaluator;
    private List<Row> tableRows;

    /**
     * @param sourceRows the rows of a relation that is not a catalog table, which its statistics are proven
     *                   over, or null for a catalog table — its rows are read from storage when first asked
     */
    CountStatisticsBound(final QueryExecutor executor, final FrostlakeParser.SelectClauseContext ctx,
                         final Table table, final Map<String, Table> aliasToTable, final String whereExpr,
                         final boolean joined, final List<Row> sourceRows) {
        this.executor = executor;
        this.ctx = ctx;
        this.table = table;
        this.aliasToTable = aliasToTable;
        this.whereExpr = whereExpr;
        this.joined = joined;
        this.sourceRows = sourceRows;
    }

    /** Whether the clause reads past its table's statistics bound. */
    boolean isUnbounded() {
        if (unbounded == null) {
            if (deciding) {
                // Asked again while the WHERE is being proven, which cannot be answered yet: nothing is
                // taken as read within the statistics meanwhile.
                return true;
            }
            deciding = true;
            try {
                unbounded = decide();
            } finally {
                deciding = false;
            }
        }
        return unbounded;
    }

    private boolean decide() {
        if (joined) {
            return true;
        }
        if (table == null) {
            return false;
        }
        if (table.isCatalogResident()) {
            if (executor.baseTableRowCount(table) == null) {
                return false;
            }
        } else {
            final RelationStatistics beneath = table.getRelationStatistics();
            if (beneath == null || !beneath.readsWithinStatistics()) {
                return true;
            }
            if (beneath.sourceRowCount() == null) {
                return false;
            }
        }
        if (ctx.havingClause() != null || hasGroupingKey()) {
            return true;
        }
        if (whereExpr == null) {
            return false;
        }
        try {
            return !Boolean.TRUE.equals(verdict(ExpressionEvaluator.parse(whereExpr)));
        } catch (final RuntimeException unprovable) {
            return true;
        }
    }

    /**
     * Whether the query groups by a key: any GROUP BY list, or GROUP BY ALL beside an item that is no
     * aggregate — a constant included, which the account groups by like any other key.
     */
    private boolean hasGroupingKey() {
        final FrostlakeParser.GroupByClauseContext groupBy = ctx.groupByClause();
        if (groupBy == null) {
            return false;
        }
        if (groupBy.ALL() == null) {
            return true;
        }
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (!SelectItemAccessors.isExprItem(item)) {
                return true;
            }
            final FrostlakeParser.ExpressionContext expr = SelectItemAccessors.getItemValueExpr(item);
            if (expr != null && !executor.hasAggregateFunctionInExpression(expr)
                    && !executor.hasWindowFunctionInExpression(expr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * What the statistics prove of a predicate over every row of the table: TRUE, FALSE, or null when
     * they prove neither.
     */
    private Boolean verdict(final Expression predicate) {
        if (isConstant(predicate)) {
            return truthOf(evaluator().evaluate(predicate, new Row(new Object[table.getColumns().size()])));
        }
        if (predicate instanceof UnaryOperationExpression
                && ((UnaryOperationExpression) predicate).getOperator() == UnaryOperator.NOT) {
            final Boolean operand = verdict(((UnaryOperationExpression) predicate).getOperand());
            return operand == null ? null : !operand;
        }
        if (predicate instanceof BinaryOperationExpression) {
            final BinaryOperationExpression binary = (BinaryOperationExpression) predicate;
            if (binary.getOperator() == BinaryOperator.AND || binary.getOperator() == BinaryOperator.OR) {
                return connective(binary.getOperator() == BinaryOperator.OR,
                    verdict(binary.getLeft()), verdict(binary.getRight()));
            }
            return comparison(binary.getLeft(), binary.getOperator(), binary.getRight());
        }
        if (predicate instanceof BetweenExpression) {
            final BetweenExpression between = (BetweenExpression) predicate;
            final Boolean inside = connective(false,
                comparison(between.getValue(), BinaryOperator.GREATER_THAN_OR_EQUAL, between.getLower()),
                comparison(between.getValue(), BinaryOperator.LESS_THAN_OR_EQUAL, between.getUpper()));
            return inside == null || !between.isNot() ? inside : !inside;
        }
        if (predicate instanceof IsNullExpression) {
            return nullTest((IsNullExpression) predicate);
        }
        return null;
    }

    /** AND, which a FALSE side decides, or OR, which a TRUE side decides. */
    private static Boolean connective(final boolean or, final Boolean left, final Boolean right) {
        if (Boolean.valueOf(or).equals(left) || Boolean.valueOf(or).equals(right)) {
            return or;
        }
        return left != null && right != null ? !or : null;
    }

    /** IS [NOT] NULL over a column that holds no NULL, or nothing else. */
    private Boolean nullTest(final IsNullExpression test) {
        final int index = columnIndexOf(test.getOperand());
        if (index < 0) {
            return null;
        }
        int nulls = 0;
        for (final Row row : tableRows()) {
            if (valueAt(row, index) == null) {
                nulls++;
            }
        }
        if (tableRows().isEmpty() || nulls > 0 && nulls < tableRows().size()) {
            return null;
        }
        final boolean allNull = nulls > 0;
        return test.isNot() ? !allNull : allNull;
    }

    /**
     * A comparison between a column and a constant, either way round, or a column and itself — and
     * failing those, one the two sides' intervals settle.
     */
    private Boolean comparison(final Expression left, final BinaryOperator op, final Expression right) {
        final BinaryOperator reversed = reversed(op);
        if (reversed == null) {
            return null;
        }
        final int leftIndex = columnIndexOf(left);
        final int rightIndex = columnIndexOf(right);
        if (leftIndex >= 0 && leftIndex == rightIndex) {
            // Holds of every value or of none, and the column holds no NULL for either.
            if (boundsOf(leftIndex) == null) {
                return null;
            }
            return op == BinaryOperator.EQUAL || op == BinaryOperator.LESS_THAN_OR_EQUAL
                || op == BinaryOperator.GREATER_THAN_OR_EQUAL;
        }
        if (leftIndex >= 0 && isConstant(right)) {
            return againstConstant(leftIndex, op, right);
        }
        if (rightIndex >= 0 && isConstant(left)) {
            return againstConstant(rightIndex, reversed, left);
        }
        return byIntervals(left, op, right);
    }

    /**
     * A comparison of exact numbers settled by the intervals the columns' least and greatest values
     * propagate through each side, where neither side may be NULL: over a column running from 1.5 to 5.5
     * live proves {@code c + 1 > 0}, {@code ABS(c) > 0} and {@code c::NUMBER(10,1) > 0}, and over a column
     * holding a NULL it proves nothing.
     */
    private Boolean byIntervals(final Expression left, final BinaryOperator op, final Expression right) {
        final ValueRange a = evaluator().inferStaticRange(left);
        final ValueRange b = evaluator().inferStaticRange(right);
        if (a == null || b == null || a.isNullable() || b.isNullable()) {
            return null;
        }
        if (op == BinaryOperator.EQUAL || op == BinaryOperator.NOT_EQUAL) {
            final Boolean equal = a.equalTo(b);
            return equal == null || op == BinaryOperator.EQUAL ? equal : Boolean.valueOf(!equal.booleanValue());
        }
        if (op == BinaryOperator.LESS_THAN || op == BinaryOperator.LESS_THAN_OR_EQUAL) {
            return a.below(b, op == BinaryOperator.LESS_THAN_OR_EQUAL);
        }
        if (op == BinaryOperator.GREATER_THAN || op == BinaryOperator.GREATER_THAN_OR_EQUAL) {
            return b.below(a, op == BinaryOperator.GREATER_THAN_OR_EQUAL);
        }
        return null;
    }

    /**
     * A column against a constant. Between the column's least and greatest value an ordering comparison
     * is monotone, so it holds of every row when it holds at both ends and of none when it fails at both.
     * An equality holds throughout only when both ends equal the constant, and fails throughout when the
     * constant lies outside them; an inequality the other way round.
     */
    private Boolean againstConstant(final int index, final BinaryOperator op, final Expression constant) {
        if (!orderedAsCompared(index, constant)) {
            return null;
        }
        final List<Object> bounds = boundsOf(index);
        if (bounds == null) {
            return null;
        }
        final Boolean atLeast = holdsAt(index, bounds.get(0), op, constant);
        final Boolean atGreatest = holdsAt(index, bounds.get(1), op, constant);
        if (atLeast == null || atGreatest == null) {
            return null;
        }
        if (op == BinaryOperator.EQUAL || op == BinaryOperator.NOT_EQUAL) {
            final boolean equalThroughout = op == BinaryOperator.EQUAL
                ? atLeast && atGreatest : !atLeast && !atGreatest;
            if (equalThroughout) {
                return op == BinaryOperator.EQUAL;
            }
            final boolean outside =
                Boolean.TRUE.equals(holdsAt(index, bounds.get(1), BinaryOperator.LESS_THAN, constant))
                || Boolean.TRUE.equals(holdsAt(index, bounds.get(0), BinaryOperator.GREATER_THAN, constant));
            return outside ? op == BinaryOperator.NOT_EQUAL : null;
        }
        return atLeast.equals(atGreatest) ? atLeast : null;
    }

    /**
     * Whether the column's own order is the order the comparison sees. A NUMBER or a date and time column
     * converts the constant to its own type; a string column must meet a string, since beside a number
     * every VALUE would be converted instead, and those order differently. An approximate number, a
     * collated string and every other family stay unproven.
     */
    private boolean orderedAsCompared(final int index, final Expression constant) {
        final TableColumn column = table.getColumns().get(index);
        final DataType declared = column.getDataType();
        if (declared instanceof NumericType) {
            return !NumericType.isApproximate(declared);
        }
        if (declared instanceof DateTimeType) {
            return true;
        }
        return declared instanceof StringType && column.getCollation() == null
            && evaluator().inferStaticType(constant) instanceof StringType;
    }

    /** The comparison where the column holds {@code value}: TRUE, FALSE, or null. */
    private Boolean holdsAt(final int index, final Object value, final BinaryOperator op,
                            final Expression constant) {
        final Object[] values = new Object[table.getColumns().size()];
        values[index] = value;
        final Expression column = new ColumnReferenceExpression(table.getColumns().get(index).getName());
        return truthOf(evaluator().evaluate(new BinaryOperationExpression(column, op, constant), new Row(values)));
    }

    /**
     * The least and the greatest value a column holds, or null when it holds a NULL or nothing at all:
     * the statistics then prove no comparison over it.
     */
    private List<Object> boundsOf(final int index) {
        Object least = null;
        Object greatest = null;
        for (final Row row : tableRows()) {
            final Object value = valueAt(row, index);
            if (value == null) {
                return null;
            }
            if (least == null || ValueComparisons.compareValues(value, least) < 0) {
                least = value;
            }
            if (greatest == null || ValueComparisons.compareValues(value, greatest) > 0) {
                greatest = value;
            }
        }
        return least == null ? null : Arrays.asList(least, greatest);
    }

    /** The table column a plain reference names, or -1 for anything else. */
    private int columnIndexOf(final Expression expr) {
        if (!(expr instanceof ColumnReferenceExpression)) {
            return -1;
        }
        final ColumnReferenceExpression ref = (ColumnReferenceExpression) expr;
        if (ref.getPositionalOrdinal() != 0 || ref.getColumnName() == null
                || ref.getTableName() != null && !namesThisTable(ref.getTableName())
                || !table.hasColumn(ref.getColumnName())) {
            return -1;
        }
        return table.getColumnIndex(ref.getColumnName());
    }

    /** Whether a qualifier names the table, by its name or by the alias the FROM clause gave it. */
    private boolean namesThisTable(final String qualifier) {
        if (qualifier.equalsIgnoreCase(table.getName())) {
            return true;
        }
        for (final Map.Entry<String, Table> alias : aliasToTable.entrySet()) {
            if (alias.getValue() == table && qualifier.equalsIgnoreCase(alias.getKey())) {
                return true;
            }
        }
        return false;
    }

    /** The comparison with its operands swapped — {@code a < b} is {@code b > a} — or null for any other. */
    private static BinaryOperator reversed(final BinaryOperator op) {
        if (op == BinaryOperator.EQUAL || op == BinaryOperator.NOT_EQUAL) {
            return op;
        }
        if (op == BinaryOperator.LESS_THAN) {
            return BinaryOperator.GREATER_THAN;
        }
        if (op == BinaryOperator.LESS_THAN_OR_EQUAL) {
            return BinaryOperator.GREATER_THAN_OR_EQUAL;
        }
        if (op == BinaryOperator.GREATER_THAN) {
            return BinaryOperator.LESS_THAN;
        }
        if (op == BinaryOperator.GREATER_THAN_OR_EQUAL) {
            return BinaryOperator.LESS_THAN_OR_EQUAL;
        }
        return null;
    }

    /**
     * A literal, a scalar subquery the planner folds, or casts, signs, NOT, EXISTS and operators over
     * those only.
     */
    private boolean isConstant(final Expression expr) {
        if (expr instanceof LiteralExpression) {
            return true;
        }
        if (expr instanceof SubqueryExpression) {
            return ConstantSubqueries.folds(executor, (SubqueryExpression) expr);
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

    private static Boolean truthOf(final Object value) {
        return value instanceof Boolean ? (Boolean) value : null;
    }

    private static Object valueAt(final Row row, final int index) {
        return index < row.size() ? row.getValue(index) : null;
    }

    private List<Row> tableRows() {
        if (tableRows == null) {
            tableRows = sourceRows != null ? sourceRows
                : executor.readTableRowsForTransaction(table.getQualifiedName());
        }
        return tableRows;
    }

    private ExpressionEvaluator evaluator() {
        if (evaluator == null) {
            evaluator = new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(),
                executor);
        }
        return evaluator;
    }
}
