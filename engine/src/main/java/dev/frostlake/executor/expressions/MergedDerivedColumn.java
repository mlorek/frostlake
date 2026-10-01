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

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.MergedRelationStatistics;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.values.RelationStatistics;

/**
 * The select item a derived relation's column — a subquery's, a CTE's, a view's — stands for where the account's
 * plan merges the relation into the query over it, which it does for a relation projecting one table's rows at
 * most under a WHERE (see {@link RelationStatistics}); a column such a relation passes through unchanged is
 * followed to the item beneath it. The shape rules read a merged column as its item: {@code SUM(x)} over
 * {@code (SELECT n + 1 x FROM t)} is rewritten as {@code SUM(n + 1)} is, and {@code SUM(x + 0)} over it is a SUM
 * over no column at all (live-verified). A relation the plan keeps whole — one that is DISTINCT, groups, limits,
 * unites or joins — has columns of its own.
 */
final class MergedDerivedColumn {

    /** How many merged relations deep a column is followed. */
    private static final int MAX_DEPTH = 32;

    private final Expression item;
    private final ExpressionEvaluatorVisitor scope;

    private MergedDerivedColumn(final Expression item, final ExpressionEvaluatorVisitor scope) {
        this.item = item;
        this.scope = scope;
    }

    /**
     * The item a column reference stands for.
     *
     * @param ref     a column reference
     * @param visitor the scope the reference is read in
     * @return the item, read in the relation beneath, or null where the reference is no column of a merged relation
     */
    static MergedDerivedColumn of(final ColumnReferenceExpression ref, final ExpressionEvaluatorVisitor visitor) {
        if (ref.getPositionalOrdinal() != 0 || ref.getColumnName() == null) {
            return null;
        }
        ExpressionEvaluatorVisitor reading = visitor;
        Table relation = visitor.declaredOwner(ref);
        String name = ref.getColumnName();
        MergedDerivedColumn found = null;
        for (int depth = 0; depth < MAX_DEPTH && relation != null && !relation.isCatalogResident(); depth++) {
            final RelationStatistics held = relation.getRelationStatistics();
            final MergedRelationStatistics statistics = held instanceof MergedRelationStatistics
                ? (MergedRelationStatistics) held : null;
            final Table source = statistics == null ? null : statistics.sourceRelation();
            if (source == null || !relation.hasColumn(name)) {
                return found;
            }
            final int index = relation.getColumnIndex(name);
            final Expression item = itemAt(statistics, source, index);
            if (item == null) {
                return found;
            }
            reading = reading.overRelation(source);
            found = new MergedDerivedColumn(item, reading);
            if (!found.isColumn() || !source.hasColumn(((ColumnReferenceExpression) item).getColumnName())) {
                return found;
            }
            relation = source;
            name = ((ColumnReferenceExpression) item).getColumnName();
        }
        return found;
    }

    /** The item at a position of a merged relation, or null where none is known. */
    private static Expression itemAt(final MergedRelationStatistics statistics, final Table source, final int index) {
        final String text = statistics.columnSource(index);
        if (text == null) {
            // A relation passing every column of its source through holds the source's column at the position.
            return statistics.passesStoredColumn(index) && index < source.getColumns().size()
                ? new ColumnReferenceExpression(source.getColumns().get(index).getName()) : null;
        }
        try {
            return ExpressionEvaluator.parse(text);
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    /** @return the item, as written */
    Expression getItem() {
        return item;
    }

    /** @return the scope of the relation beneath, which the item is read in */
    ExpressionEvaluatorVisitor getScope() {
        return scope;
    }

    /** @return whether the item is a column of the relation beneath, which the merged column then is too */
    boolean isColumn() {
        return item instanceof ColumnReferenceExpression
            && ((ColumnReferenceExpression) item).getPositionalOrdinal() == 0
            && ((ColumnReferenceExpression) item).getColumnName() != null;
    }
}
