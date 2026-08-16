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
import dev.frostlake.metastore.model.Table;
import dev.frostlake.values.RelationStatistics;

import java.util.List;

/**
 * The statistics a SELECT's result keeps of the catalog table it reads (see {@link RelationStatistics}):
 * the clause projects the rows of one table — or of one relation keeping such statistics — and filters
 * them by at most a WHERE. Whether that WHERE lets every row through is the clause's own statistics
 * bound, asked only when a query over the result needs it, so no other query reads the table for it.
 */
final class DerivedStatistics implements RelationStatistics {

    private final QueryExecutor executor;
    private final CountStatisticsBound clauseBound;
    private final Table source;
    private final List<String> columnSources;

    /**
     * @param executor      the executor that reads the table's rows
     * @param clauseBound   the reading clause's statistics bound, or null when every row reaches the result
     * @param source        the relation the clause reads: a catalog table, or a relation keeping its statistics
     * @param columnSources each result column's select item as written, or null when every column is the
     *                      source's own
     */
    DerivedStatistics(final QueryExecutor executor, final CountStatisticsBound clauseBound, final Table source,
                      final List<String> columnSources) {
        this.executor = executor;
        this.clauseBound = clauseBound;
        this.source = source;
        this.columnSources = columnSources;
    }

    @Override
    public boolean readsWithinStatistics() {
        return clauseBound == null || !clauseBound.isUnbounded();
    }

    @Override
    public Long sourceRowCount() {
        if (source.isCatalogResident()) {
            return executor.baseTableRowCount(source);
        }
        final RelationStatistics beneath = source.getRelationStatistics();
        return beneath == null ? null : beneath.sourceRowCount();
    }

    @Override
    public boolean passesStoredColumn(final int index) {
        if (columnSources == null) {
            return true;
        }
        return index >= 0 && index < columnSources.size() && passesStoredColumn(source, columnSources.get(index));
    }

    /**
     * Whether a select item is a plain reference to one of the source's stored columns: a catalog
     * table's column, or a column a relation over it passes through unchanged.
     */
    private static boolean passesStoredColumn(final Table source, final String itemText) {
        if (itemText == null) {
            return false;
        }
        final Expression item;
        try {
            item = ExpressionEvaluator.parse(itemText);
        } catch (final RuntimeException unreadable) {
            return false;
        }
        if (!(item instanceof ColumnReferenceExpression)
                || ((ColumnReferenceExpression) item).getPositionalOrdinal() != 0) {
            return false;
        }
        final String name = ((ColumnReferenceExpression) item).getColumnName();
        if (name == null || !source.hasColumn(name)) {
            return false;
        }
        if (source.isCatalogResident()) {
            return true;
        }
        final RelationStatistics beneath = source.getRelationStatistics();
        return beneath != null && beneath.passesStoredColumn(source.getColumnIndex(name));
    }
}
