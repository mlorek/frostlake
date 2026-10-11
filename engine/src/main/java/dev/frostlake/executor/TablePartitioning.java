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
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a table function called with {@code OVER} reads its input (live-verified): {@code PARTITION BY} names
 * the columns whose values make a partition, {@code ORDER BY} the order a partition's rows are read in. A
 * partitioning key is a column of a relation to the call's left, or a constant - {@code Invalid partitioning
 * expression 'UPPER(EV.G)'; must be a column.} otherwise - and a frame is a syntax error at its first word.
 * The rows a handler writes once a partition is through are joined to a row that keeps the partition's own
 * columns and holds NULL in every other.
 */
final class TablePartitioning {

    private final List<FrostlakeParser.ExpressionContext> partitionKeys;
    private final List<FrostlakeParser.OrderItemContext> orderItems;
    private final List<Integer> keptSlots;
    private final int leftWidth;

    private TablePartitioning(final List<FrostlakeParser.ExpressionContext> partitionKeys,
                              final List<FrostlakeParser.OrderItemContext> orderItems,
                              final List<Integer> keptSlots, final int leftWidth) {
        this.partitionKeys = partitionKeys;
        this.orderItems = orderItems;
        this.keptSlots = keptSlots;
        this.leftWidth = leftWidth;
    }

    /**
     * @param over        the call's OVER clause, or null for a call written without one
     * @param scope       the relations to the call's left, by the names they answer to
     * @param scopeTables those relations in combined-row order
     * @return the partitioning the clause asks for
     */
    static TablePartitioning of(final FrostlakeParser.OverClauseContext over, final Map<String, Table> scope,
                                final List<Table> scopeTables) {
        int width = 0;
        for (final Table table : scopeTables) {
            width += table.getColumns().size();
        }
        final List<FrostlakeParser.ExpressionContext> keys = new ArrayList<>();
        final List<FrostlakeParser.OrderItemContext> order = new ArrayList<>();
        final List<Integer> kept = new ArrayList<>();
        if (over == null) {
            return new TablePartitioning(keys, order, kept, width);
        }
        if (over.windowFrame() != null) {
            final Token word = over.windowFrame().getStart();
            throw new RuntimeException(SqlCompilationError.of("syntax error line " + word.getLine() + " at position "
                + word.getCharPositionInLine() + " unexpected '" + word.getText() + "'."));
        }
        if (over.partitionByClause() != null) {
            for (final FrostlakeParser.ExpressionContext key : over.partitionByClause().expressionList().expression()) {
                final boolean column = key instanceof FrostlakeParser.QualifiedNameExprContext;
                if (!column && namesAColumn(key)) {
                    throw new RuntimeException("Invalid partitioning expression '"
                        + ParseTreeText.getOriginalText(key).toUpperCase(Locale.ROOT) + "'; must be a column.");
                }
                keys.add(key);
                if (column) {
                    final int slot = slotOf((FrostlakeParser.QualifiedNameExprContext) key, scope, scopeTables);
                    if (slot >= 0) {
                        kept.add(Integer.valueOf(slot));
                    }
                }
            }
        }
        if (over.orderByClause() != null) {
            order.addAll(over.orderByClause().orderItem());
        }
        return new TablePartitioning(keys, order, kept, width);
    }

    List<FrostlakeParser.ExpressionContext> partitionKeys() {
        return partitionKeys;
    }

    List<FrostlakeParser.OrderItemContext> orderItems() {
        return orderItems;
    }

    /** Whether the clause names no partitioning key, so the whole input is one partition. */
    boolean isWholeInput() {
        return partitionKeys.isEmpty();
    }

    /**
     * The input's rows by partition, each partition's rows in the order they are read.
     *
     * @param inputs       the input's rows, in the order the stage was handed them
     * @param explicit     whether the call is written with OVER
     * @param onePartition for a call without OVER, whether the whole input is one partition rather than each
     *                     row its own
     * @return the partitions, in the order their first rows arrived
     */
    List<List<TableFunctionInput>> partitions(final List<TableFunctionInput> inputs, final boolean explicit,
                                              final boolean onePartition) {
        final List<List<TableFunctionInput>> partitions = new ArrayList<>();
        if (inputs.isEmpty()) {
            return partitions;
        }
        if (!explicit) {
            if (onePartition) {
                partitions.add(inputs);
                return partitions;
            }
            for (final TableFunctionInput input : inputs) {
                final List<TableFunctionInput> own = new ArrayList<>();
                own.add(input);
                partitions.add(own);
            }
            return partitions;
        }
        final Map<List<Object>, List<TableFunctionInput>> byKey = new LinkedHashMap<>();
        for (final TableFunctionInput input : inputs) {
            List<TableFunctionInput> partition = byKey.get(input.partitionKey());
            if (partition == null) {
                partition = new ArrayList<>();
                byKey.put(input.partitionKey(), partition);
            }
            partition.add(input);
        }
        for (final List<TableFunctionInput> partition : byKey.values()) {
            if (!orderItems.isEmpty()) {
                Collections.sort(partition, new Comparator<TableFunctionInput>() {
                    @Override
                    public int compare(final TableFunctionInput one, final TableFunctionInput other) {
                        for (int i = 0; i < orderItems.size(); i++) {
                            final FrostlakeParser.OrderItemContext item = orderItems.get(i);
                            final int compared = ValueComparisons.compareOrderKey(one.orderKey().get(i),
                                other.orderKey().get(i), item.DESC() == null, ValueComparisons.nullsFirstFlag(item));
                            if (compared != 0) {
                                return compared;
                            }
                        }
                        return 0;
                    }
                });
            }
            partitions.add(partition);
        }
        return partitions;
    }

    /**
     * @param leftRow a row of the partition
     * @return the left side of a row written once the partition is through: the partition's own columns, and
     *         NULL in every other
     */
    List<Object> endRow(final Row leftRow) {
        final List<Object> values = new ArrayList<>(leftWidth);
        for (int i = 0; i < leftWidth; i++) {
            values.add(null);
        }
        for (final Integer slot : keptSlots) {
            if (slot.intValue() < leftRow.getValues().size()) {
                values.set(slot.intValue(), leftRow.getValue(slot.intValue()));
            }
        }
        return values;
    }

    /** Whether an expression names a column anywhere outside the queries nested in it. */
    private static boolean namesAColumn(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (namesAColumn(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /** The combined row's slot a column reference reads, or -1 when it names no column of the relations. */
    static int slotOf(final FrostlakeParser.QualifiedNameExprContext reference, final Map<String, Table> scope,
                      final List<Table> scopeTables) {
        final String[] parts = ParseTreeText.qualifiedNameParts(reference.qualifiedName());
        if (parts == null || parts.length == 0) {
            return -1;
        }
        final String column = parts[parts.length - 1];
        Table owner = null;
        if (parts.length > 1) {
            final String qualifier = parts[parts.length - 2];
            for (final Map.Entry<String, Table> entry : scope.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(qualifier) || entry.getValue().getName() != null
                        && entry.getValue().getName().equalsIgnoreCase(qualifier)) {
                    owner = entry.getValue();
                    break;
                }
            }
            if (owner == null) {
                return -1;
            }
        }
        int offset = 0;
        for (final Table table : scopeTables) {
            if (owner == null || owner == table) {
                for (int i = 0; i < table.getColumns().size(); i++) {
                    if (table.getColumns().get(i).getName().equalsIgnoreCase(column)) {
                        return offset + i;
                    }
                }
            }
            offset += table.getColumns().size();
        }
        return -1;
    }
}
