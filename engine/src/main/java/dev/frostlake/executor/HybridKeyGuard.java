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

import dev.frostlake.metastore.HybridKeys;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.UniqueConstraint;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The keys a hybrid table holds as a statement writes it (documented: its PRIMARY KEY, UNIQUE and FOREIGN KEY
 * constraints are enforced row by row, and a foreign key takes RESTRICT and NO ACTION alone, for an UPDATE and a
 * DELETE alike). An INSERT's row names a parent row with each of its foreign keys; an UPDATE leaves the table holding
 * no primary or UNIQUE key twice and its rows naming parent rows; and neither an UPDATE nor a DELETE takes away a key
 * value that a row of a hybrid child table still names. Each refusal is the one the key's index answers (see
 * HybridKeys), raised before the statement writes any row.
 */
final class HybridKeyGuard {

    private final QueryExecutor executor;

    /**
     * @param executor the executor whose catalog and storage hold the tables
     */
    HybridKeyGuard(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * A hybrid table's foreign keys hold row by row (documented): a row whose key no row of the parent holds, a NULL
     * key included, is refused, the parent rows this transaction has written counted.
     */
    void refuseOrphanRow(final Table table, final String fullyQualifiedName, final Row row) {
        if (!table.isHybrid()) {
            return;
        }
        for (final ForeignKeyConstraint key : keysOf(table, fullyQualifiedName)) {
            final List<Object> values = new ArrayList<>();
            boolean missing = false;
            for (final String column : key.getColumnNames()) {
                final int index = table.getColumnIndex(column);
                final Object value = index >= 0 && index < row.getValues().size() ? row.getValue(index) : null;
                missing = missing || value == null;
                values.add(value);
            }
            if (missing || !parentHolds(key, values)) {
                throw new RuntimeException(HybridKeys.foreignKeyViolated(table, key));
            }
        }
    }

    /**
     * Every foreign key a table holds: those declared on the table, and the one-column key each column declares with
     * REFERENCES - the same key, spelled on the column (documented). A column's parent is read from the table's
     * schema, and its referenced column is the parent's primary key where the column names none.
     */
    private List<ForeignKeyConstraint> keysOf(final Table table, final String fullyQualifiedName) {
        final List<ForeignKeyConstraint> keys = new ArrayList<>(table.getForeignKeys());
        final String[] home = QualifiedName.parse(fullyQualifiedName).parts();
        for (final TableColumn column : table.getColumns()) {
            if (column.getReferencedTable() == null || home.length != 3) {
                continue;
            }
            final String[] written = QualifiedName.parse(column.getReferencedTable()).parts();
            final String parent = written.length >= 3 ? column.getReferencedTable()
                : written.length == 2 ? QualifiedName.join(home[0], written[0], written[1])
                : QualifiedName.join(home[0], home[1], written[0]);
            final String referenced = column.getReferencedColumn() != null ? column.getReferencedColumn()
                : primaryKeyColumn(parent);
            if (referenced != null) {
                keys.add(new ForeignKeyConstraint(table.columnForeignKeyConstraintName(column.getName()),
                    Arrays.asList(column.getName()), parent, Arrays.asList(referenced), null, null));
            }
        }
        return keys;
    }

    /** The first primary-key column of a parent table, or null when it has none or is gone. */
    private String primaryKeyColumn(final String parentName) {
        final Table parent;
        try {
            parent = executor.getCatalog().resolveTable(parentName);
        } catch (final RuntimeException gone) {
            return null;
        }
        for (final TableColumn column : parent.getColumns()) {
            if (column.isPrimaryKey()) {
                return column.getName();
            }
        }
        return null;
    }

    /** Whether a row of the key's parent holds these values in its referenced columns. */
    private boolean parentHolds(final ForeignKeyConstraint key, final List<Object> values) {
        final String parentName = key.getReferencedTable();
        final Table parent = executor.getCatalog().resolveTable(parentName);
        final String parentStorage = executor.getFullyQualifiedTableName(parentName);
        final List<Integer> slots = new ArrayList<>();
        for (final String column : key.getReferencedColumns()) {
            slots.add(parent.getColumnIndex(column));
        }
        final List<Row> candidates = new ArrayList<>(executor.getStorageEngine().getTableStorage(parentStorage).scan());
        if (executor.isDeferredApply()) {
            candidates.addAll(executor.getTransactionManager().getCurrentTransaction().getWriteSet()
                .pendingInserts(parentStorage));
        }
        for (final Row candidate : candidates) {
            boolean match = true;
            for (int i = 0; i < slots.size() && match; i++) {
                final int slot = slots.get(i);
                final Object held = slot >= 0 && slot < candidate.getValues().size() ? candidate.getValue(slot) : null;
                match = held != null && ValueComparisons.compareValues(held, values.get(i)) == 0;
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    /**
     * Refuse an UPDATE of a hybrid table that would break one of its keys, before any row is written: the rows it
     * leaves the table holding repeat no primary key and no UNIQUE key, every row it writes names a parent row with
     * each foreign key, and no row it changes takes away a key value a child row still names.
     *
     * @param table   the table
     * @param name    its full name
     * @param rows    every row the table holds as the statement sees it
     * @param changed the rows the UPDATE changes, among {@code rows} by identity
     * @param images  their new images, in the same order
     */
    void requireUpdate(final Table table, final String name, final List<Row> rows, final List<Row> changed,
                       final List<Row> images) {
        if (!table.isHybrid() || changed.isEmpty()) {
            return;
        }
        final Map<Row, Row> imageOf = new IdentityHashMap<>();
        for (int i = 0; i < changed.size(); i++) {
            imageOf.put(changed.get(i), images.get(i));
        }
        final List<Row> after = new ArrayList<>(rows.size());
        for (final Row row : rows) {
            final Row image = imageOf.get(row);
            after.add(image != null ? image : row);
        }
        requireDistinctKeys(table, after);
        for (final Row image : images) {
            refuseOrphanRow(table, name, image);
        }
        refuseReferencedRows(table, name, changed, after);
    }

    /**
     * Refuse a DELETE of a hybrid table's rows while a child row still names a key value only they hold, before any
     * row is deleted.
     *
     * @param table   the table
     * @param name    its full name
     * @param rows    every row the table holds as the statement sees it
     * @param deleted the rows the DELETE takes away, among {@code rows} by identity
     */
    void requireDelete(final Table table, final String name, final List<Row> rows, final List<Row> deleted) {
        if (!table.isHybrid() || deleted.isEmpty()) {
            return;
        }
        final Set<Row> leaving = Collections.newSetFromMap(new IdentityHashMap<Row, Boolean>());
        leaving.addAll(deleted);
        final List<Row> kept = new ArrayList<>();
        for (final Row row : rows) {
            if (!leaving.contains(row)) {
                kept.add(row);
            }
        }
        refuseReferencedRows(table, name, deleted, kept);
    }

    /** Refuse rows that repeat the primary key or a UNIQUE key; a key holding a NULL repeats none. */
    private static void requireDistinctKeys(final Table table, final List<Row> rows) {
        if (!table.getPrimaryKeys().isEmpty()) {
            refuseRepeated(table, rows, table.getPrimaryKeys(), HybridKeys.primaryKeyExists());
        }
        for (final UniqueConstraint key : HybridKeys.uniqueKeys(table)) {
            refuseRepeated(table, rows, key.getColumnNames(), HybridKeys.uniqueViolated(table, key));
        }
    }

    private static void refuseRepeated(final Table table, final List<Row> rows, final List<String> columns,
                                       final String refusal) {
        final Set<List<Object>> seen = new HashSet<>();
        for (final Row row : rows) {
            final List<Object> value = HybridKeys.keyOf(table, columns, row);
            if (value != null && !seen.add(value)) {
                throw new RuntimeException(refusal);
            }
        }
    }

    /**
     * Refuse rows leaving a hybrid parent while a row of a hybrid table of its database names, by a foreign key, a key
     * value they held that no row the parent keeps holds: refused as that child's key, which takes RESTRICT and NO
     * ACTION alone.
     *
     * @param parent     the parent
     * @param parentName its full name
     * @param leaving    the rows as they were before the statement
     * @param kept       every row the parent holds after it
     */
    private void refuseReferencedRows(final Table parent, final String parentName, final List<Row> leaving,
                                      final List<Row> kept) {
        final String[] home = QualifiedName.parse(parentName).parts();
        final Database database = home.length == 3 ? executor.getCatalog().getDatabase(home[0]) : null;
        if (database == null) {
            return;
        }
        for (final Schema schema : database.getAllSchemas()) {
            for (final Table child : schema.getTables()) {
                if (!child.isHybrid()) {
                    continue;
                }
                final String childName = QualifiedName.join(home[0], schema.getName(), child.getName());
                for (final ForeignKeyConstraint key : keysOf(child, childName)) {
                    if (namesParent(key, parent)) {
                        refuseNamedRows(child, childName, key, parent, leaving, kept);
                    }
                }
            }
        }
    }

    /** Whether a foreign key names this table as its parent. */
    private boolean namesParent(final ForeignKeyConstraint key, final Table parent) {
        try {
            return executor.getCatalog().resolveTable(key.getReferencedTable()) == parent;
        } catch (final RuntimeException gone) {
            return false;
        }
    }

    /** Refuse the leaving rows' key values that a child row names and no kept row holds. */
    private void refuseNamedRows(final Table child, final String childName, final ForeignKeyConstraint key,
                                 final Table parent, final List<Row> leaving, final List<Row> kept) {
        final Set<List<Object>> held = new HashSet<>();
        for (final Row row : kept) {
            final List<Object> value = HybridKeys.keyOf(parent, key.getReferencedColumns(), row);
            if (value != null) {
                held.add(value);
            }
        }
        final Set<List<Object>> gone = new HashSet<>();
        for (final Row row : leaving) {
            final List<Object> value = HybridKeys.keyOf(parent, key.getReferencedColumns(), row);
            if (value != null && !held.contains(value)) {
                gone.add(value);
            }
        }
        if (gone.isEmpty()) {
            return;
        }
        for (final Row row : storedRows(childName)) {
            final List<Object> value = HybridKeys.keyOf(child, key.getColumnNames(), row);
            if (value != null && gone.contains(value)) {
                throw new RuntimeException(HybridKeys.foreignKeyViolated(child, key));
            }
        }
    }

    /** A table's stored rows, with the rows this transaction has inserted but not committed. */
    private List<Row> storedRows(final String name) {
        final String storage = executor.getFullyQualifiedTableName(name);
        final List<Row> rows = new ArrayList<>(executor.getStorageEngine().getTableStorage(storage).scan());
        if (executor.isDeferredApply()) {
            rows.addAll(executor.getTransactionManager().getCurrentTransaction().getWriteSet()
                .pendingInserts(storage));
        }
        return rows;
    }
}
