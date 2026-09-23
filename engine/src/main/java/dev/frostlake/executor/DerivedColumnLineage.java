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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.values.RelationStatistics;

/**
 * What a derived relation's column — a subquery's, a CTE's, a view's — reads, followed through the relations the
 * account's plan merges into the query over them (see {@link MergedRelationStatistics}): a stored column of the
 * catalog table beneath, passed through unchanged at every level, by name or by position, or a literal the column
 * projects. The planner reads the stored column's statistics for the derived one whatever a WHERE on the way keeps:
 * a column passing a one-valued VARCHAR, FLOAT or DATE column through is one value on every row, and one passing a
 * column that holds two values through varies though {@code WHERE t = 'x'} leaves one. Any other item — an
 * expression, a call, a column of a relation the plan keeps whole — has no lineage here.
 */
public final class DerivedColumnLineage {

    /** How many merged relations deep a column is followed. */
    private static final int MAX_DEPTH = 32;

    private final Table storedTable;
    private final String storedColumn;

    private DerivedColumnLineage(final Table storedTable, final String storedColumn) {
        this.storedTable = storedTable;
        this.storedColumn = storedColumn;
    }

    /**
     * The lineage of a derived relation's column.
     *
     * @param relation a relation that is no catalog table
     * @param column   the column's name
     * @return the lineage, or null where the column neither passes a stored column through nor projects a literal
     */
    public static DerivedColumnLineage of(final Table relation, final String column) {
        Table current = relation;
        String name = column;
        for (int depth = 0; depth < MAX_DEPTH && current != null && name != null; depth++) {
            if (current.residentSource() != null) {
                return depth == 0 ? null : new DerivedColumnLineage(current.residentSource(), name);
            }
            final RelationStatistics held = current.getRelationStatistics();
            if (!(held instanceof MergedRelationStatistics) || !current.hasColumn(name)) {
                return null;
            }
            final MergedRelationStatistics statistics = (MergedRelationStatistics) held;
            final Table source = statistics.sourceRelation();
            final int index = current.getColumnIndex(name);
            if (source == null || index < 0) {
                return null;
            }
            final String item = statistics.columnSource(index);
            if (item == null) {
                if (!statistics.passesStoredColumn(index) || index >= source.getColumns().size()) {
                    return null;
                }
                name = source.getColumns().get(index).getName();
            } else {
                final Expression parsed = parsed(item);
                if (parsed instanceof LiteralExpression) {
                    return ((LiteralExpression) parsed).getValue() == null ? null : new DerivedColumnLineage(null, null);
                }
                name = sourceColumn(parsed, source);
            }
            current = source;
        }
        return null;
    }

    /** The source's column an item names, by name or by position, or null where the item is no such column. */
    private static String sourceColumn(final Expression item, final Table source) {
        if (!(item instanceof ColumnReferenceExpression)) {
            return null;
        }
        final ColumnReferenceExpression reference = (ColumnReferenceExpression) item;
        final int ordinal = reference.getPositionalOrdinal();
        if (ordinal > 0) {
            return ordinal <= source.getColumns().size() ? source.getColumns().get(ordinal - 1).getName() : null;
        }
        if (ordinal != 0 || reference.getColumnName() == null || !source.hasColumn(reference.getColumnName())) {
            return null;
        }
        return source.getColumn(reference.getColumnName()).getName();
    }

    private static Expression parsed(final String item) {
        try {
            return ExpressionEvaluator.parse(item);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** @return the catalog table whose stored column the derived column passes through, or null for a literal */
    public Table storedTable() {
        return storedTable;
    }

    /** @return the stored column's name as the catalog table declares it, or null for a literal */
    public String storedColumn() {
        return storedColumn;
    }

    /** @return whether the column projects a literal that is not NULL, one value on every row */
    public boolean isLiteral() {
        return storedTable == null;
    }
}
