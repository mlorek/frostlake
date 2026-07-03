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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * ORDER BY query stage extracted from {@link QueryExecutor}. Resolves each sort key (positional
 * ordinal → N-th SELECT expression, SELECT alias → its expression, else column/expression as written),
 * materialises the per-row sort-key vector once, and stable-sorts with NULLS FIRST/LAST placement.
 * A separate post-GROUP-BY path matches ORDER BY keys to already-computed result columns. Pure value
 * comparisons come from {@link ValueComparisons}; column/qualified resolution and expression evaluation
 * are delegated back to the owning executor.
 */
final class OrderByExecutor {

    private final QueryExecutor executor;

    OrderByExecutor(final QueryExecutor executor) {
        this.executor = executor;
    }

    List<Row> orderBy(final List<Row> rows, final Table table, final FrostlakeParser.SelectStatementContext ctx,
                      final Map<String, Table> aliasToTable, final List<Table> allTables) {
        // Parse order items
        List<String> orderColumns = new ArrayList<>();
        List<Boolean> ascending = new ArrayList<>();
        final List<Boolean> nullsFirst = new ArrayList<>();

        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            // Resolve the ORDER BY key to text we can evaluate per row: a positional ordinal → the N-th
            // SELECT expression; a SELECT alias → that item's expression; otherwise the key as written (a
            // column name, or an arbitrary expression that resolveOrderValue evaluates against the row).
            String colName = resolveOrderOrdinal(item.expression().getText(), ctx);
            colName = resolveOrderAlias(colName, ctx);
            orderColumns.add(colName);
            ascending.add(item.DESC() == null); // Default is ASC
            nullsFirst.add(ValueComparisons.nullsFirstFlag(item));
        }

        if (rows.size() <= 1) {
            return rows; // nothing to compare; also avoids resolving keys for a degenerate result
        }

        // Decorate-sort-undecorate: resolve each row's sort-key vector exactly ONCE (the old comparator
        // re-resolved every column — string parse + alias scan — for both operands on every comparison,
        // i.e. O(n log n) resolutions; this is O(n)).
        final int keyCount = orderColumns.size();
        final Object[][] sortKeys = new Object[rows.size()][keyCount];
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            for (int i = 0; i < keyCount; i++) {
                sortKeys[r][i] = resolveOrderValue(row, orderColumns.get(i), table, aliasToTable, allTables);
            }
        }

        // Sort an index array against the precomputed keys (TimSort is stable, so equal keys keep their
        // original order — identical to the previous rows.sort), then rebuild the list.
        Integer[] order = new Integer[rows.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, new Comparator<Integer>() {
            @Override
            public int compare(final Integer a, final Integer b) {
                for (int i = 0; i < keyCount; i++) {
                    int cmp = ValueComparisons.compareOrderKey(sortKeys[a][i], sortKeys[b][i], ascending.get(i), nullsFirst.get(i));
                    if (cmp != 0) {
                        return cmp;
                    }
                }
                return 0;
            }
        });

        List<Row> sorted = new ArrayList<>(rows.size());
        for (int i = 0; i < order.length; i++) {
            sorted.add(rows.get(order[i]));
        }
        rows.clear();
        rows.addAll(sorted);
        return rows;
    }

    /**
     * Resolve one ORDER BY key for a row, preserving the original resolution order: qualified column
     * (table.column / alias.column), else a bare column-index lookup, else a "column not found" error.
     */
    private Object resolveOrderValue(final Row row, final String colName, final Table table,
                                     final Map<String, Table> aliasToTable, final List<Table> allTables) {
        try {
            return executor.getQualifiedColumnValueFromTables(row, allTables, aliasToTable, colName);
        } catch (final Exception e) {
            try {
                int colIndex = ValueComparisons.getColumnIndex(table, colName);
                return row.getValue(colIndex);
            } catch (final Exception e2) {
                // Not a plain column: evaluate as an expression against the row so ORDER BY can sort by an
                // arbitrary expression or function (e.g. ORDER BY a+b, ABS(a)) — Snowflake allows any
                // expression over the in-scope tables as a sort key.
                try {
                    final ExpressionEvaluator evaluator =
                        new ExpressionEvaluator(table, executor.getFunctionRegistry(), executor.getCatalog(), executor);
                    return evaluator.evaluate(colName, row);
                } catch (final Exception e3) {
                    throw new RuntimeException("Column not found in ORDER BY: " + colName, e3);
                }
            }
        }
    }

    /** ORDER BY by a SELECT alias → that item's expression text, so it can be evaluated per row. */
    private String resolveOrderAlias(final String text, final FrostlakeParser.SelectStatementContext ctx) {
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        for (final FrostlakeParser.SelectItemContext item : firstClause.selectList().selectItem()) {
            if (SelectItemAccessors.getItemAlias(item) != null
                    && ParseTreeText.getIdentifier(SelectItemAccessors.getItemAlias(item)).equalsIgnoreCase(text)) {
                final ParserRuleContext e = SelectItemAccessors.getItemExpression(item);
                if (e != null) {
                    return ParseTreeText.getOriginalText(e);
                }
            }
        }
        return text;
    }

    /** ORDER BY &lt;n&gt; positional reference: resolve to the N-th SELECT item's expression text. */
    private String resolveOrderOrdinal(final String text, final FrostlakeParser.SelectStatementContext ctx) {
        if (text == null || !text.matches("\\d+")) {
            return text;
        }
        final int n = Integer.parseInt(text);
        final FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        final FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();
        final List<FrostlakeParser.SelectItemContext> items = firstClause.selectList().selectItem();
        if (n >= 1 && n <= items.size()) {
            final ParserRuleContext e = SelectItemAccessors.getItemExpression(items.get(n - 1));
            if (e != null) {
                return e.getText();
            }
        }
        return text;
    }

    List<Row> orderByAfterGroupBy(final List<Row> rows, final FrostlakeParser.SelectStatementContext ctx) {
        // After GROUP BY, we need to match ORDER BY expressions to result column positions
        List<Integer> orderColumnIndices = new ArrayList<>();
        List<Boolean> ascending = new ArrayList<>();
        final List<Boolean> nullsFirst = new ArrayList<>();

        // Get the first selectClause to access the select list structure
        // (all UNION parts must have compatible select lists)
        FrostlakeParser.SelectOperandContext firstOp = ctx.selectOperand(0);
        FrostlakeParser.SelectClauseContext firstClause = firstOp.selectClause() != null
            ? firstOp.selectClause()
            : firstOp.selectStatement().selectOperand(0).selectClause();

        for (final FrostlakeParser.OrderItemContext item : ctx.orderByClause().orderItem()) {
            String orderExpr = item.expression().getText();
            ascending.add(item.DESC() == null);
            nullsFirst.add(ValueComparisons.nullsFirstFlag(item));

            // Try to match the ORDER BY expression to a SELECT item
            int colIndex = -1;
            List<FrostlakeParser.SelectItemContext> selectItems = firstClause.selectList().selectItem();

            // ORDER BY <n> positional reference → the N-th SELECT column.
            if (orderExpr.matches("\\d+")) {
                final int ord = Integer.parseInt(orderExpr);
                if (ord >= 1 && ord <= selectItems.size()) {
                    colIndex = ord - 1;
                }
            }

            // First try to match by alias
            for (int i = 0; i < selectItems.size(); i++) {
                if (SelectItemAccessors.getItemAlias(selectItems.get(i)) != null) {
                    String alias = ParseTreeText.getIdentifier(SelectItemAccessors.getItemAlias(selectItems.get(i)));
                    if (alias.equalsIgnoreCase(orderExpr)) {
                        colIndex = i;
                        break;
                    }
                }
            }

            // If no alias match, try by expression
            if (colIndex == -1) {
                for (int i = 0; i < selectItems.size(); i++) {
                    ParserRuleContext e = SelectItemAccessors.getItemExpression(selectItems.get(i));
                    if (e != null && e.getText().equals(orderExpr)) {
                        colIndex = i;
                        break;
                    }
                }
            }

            // Try case-insensitive expression match
            if (colIndex == -1) {
                for (int i = 0; i < selectItems.size(); i++) {
                    ParserRuleContext e = SelectItemAccessors.getItemExpression(selectItems.get(i));
                    if (e != null && e.getText().equalsIgnoreCase(orderExpr)) {
                        colIndex = i;
                        break;
                    }
                }
            }

            if (colIndex == -1) {
                throw new RuntimeException("ORDER BY expression not found in SELECT list: " + orderExpr);
            }

            orderColumnIndices.add(colIndex);
        }

        // Sort rows by the matched columns
        rows.sort((final var r1, final var r2) -> {
            for (int i = 0; i < orderColumnIndices.size(); i++) {
                int colIndex = orderColumnIndices.get(i);

                Object v1 = r1.getValue(colIndex);
                Object v2 = r2.getValue(colIndex);

                int cmp = ValueComparisons.compareOrderKey(v1, v2, ascending.get(i), nullsFirst.get(i));
                if (cmp != 0) {
                    return cmp;
                }
            }
            return 0;
        });

        return rows;
    }
}
