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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.TableStorage;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * What ALTER TABLE ... ADD COLUMN may add to a table that holds rows (live-verified): no column whose value is drawn
 * afresh for each row — AUTOINCREMENT, IDENTITY or a sequence's NEXTVAL as its default — which is refused before the
 * default itself is judged, and no NOT NULL column without a non-null default to fill the rows with. An empty table
 * takes either.
 */
final class AddedColumns {

    private AddedColumns() {
    }

    /** Whether the table stored under this name holds a row. */
    static boolean holdsRows(final QueryExecutor executor, final String storageName) {
        final TableStorage storage = executor.getStorageEngine().getTableStorage(storageName);
        return storage != null && !storage.scan().isEmpty();
    }

    /**
     * Refuse a column whose definition draws a value for each row: {@code Cannot add column 'K' with non-constant
     * default to non-empty table 'T'.}
     *
     * @param column the column's definition
     * @param name   its canonical name
     * @param table  the table's name
     */
    static void refuseNonConstantDefault(final FrostlakeParser.ColumnDefContext column, final String name,
                                         final String table) {
        for (final FrostlakeParser.ColumnConstraintContext constraint : column.columnConstraint()) {
            final boolean drawn = constraint.AUTOINCREMENT() != null || constraint.IDENTITY() != null
                || constraint.DEFAULT() != null && readsSequence(constraint.defaultExpression());
            if (drawn) {
                throw new RuntimeException(SqlCompilationError.inline("Cannot add column '" + name
                    + "' with non-constant default to non-empty table '" + table + "'."));
            }
        }
    }

    /**
     * Refuse a NOT NULL column with no default, or a NULL one, to fill the rows with: {@code Non-nullable column 'D'
     * cannot be added to non-empty table 'T' unless it has a non-null default value.}
     *
     * @param column the parsed column
     * @param table  the table's name
     */
    static void refuseUnfillableNotNull(final TableColumn column, final String table) {
        final Object fill = column.getDefaultValue();
        if (!column.isNullable() && (fill == null || "NULL".equalsIgnoreCase(String.valueOf(fill).trim()))) {
            throw new RuntimeException(SqlCompilationError.inline("Non-nullable column '" + column.getName()
                + "' cannot be added to non-empty table '" + table + "' unless it has a non-null default value."));
        }
    }

    /** Whether a default expression reads a sequence, {@code s.NEXTVAL}, anywhere in it. */
    private static boolean readsSequence(final ParseTree node) {
        if (node == null) {
            return false;
        }
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).getSymbol().getType() == FrostlakeLexer.NEXTVAL;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsSequence(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
