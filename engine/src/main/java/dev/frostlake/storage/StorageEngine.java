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

package dev.frostlake.storage;

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Table;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class StorageEngine {

    private final Map<String, TableStorage> tables;
    /**
     * The rows of each PERMANENT table a TEMPORARY table of the same name hides, under that name's key.
     * The temporary table's rows take the key itself, so every read and write by name reaches them.
     */
    private final Map<String, TableStorage> shadowed;
    // Package-private, not private: read by TableStorage, which is a top-level type in this
    // package rather than a nested one.
    boolean enforcePrimaryKey = false;
    boolean enforceUniqueKey = false;

    public StorageEngine() {
        this.tables = new ConcurrentHashMap<>();
        this.shadowed = new ConcurrentHashMap<>();
    }

    public void setEnforcePrimaryKey(final boolean enforce) { this.enforcePrimaryKey = enforce; }
    public void setEnforceUniqueKey(final boolean enforce) { this.enforceUniqueKey = enforce; }
    public boolean isEnforcePrimaryKey() { return enforcePrimaryKey; }
    public boolean isEnforceUniqueKey() { return enforceUniqueKey; }

    // Time-travel snapshots are taken per STATEMENT (see snapshotDirtyTables), not per row: a table is
    // marked dirty on each mutation and snapshotted once after the statement completes. This makes bulk
    // DML O(N) instead of O(N^2) (every insert used to deep-copy the whole table) while keeping
    // statement-level time-travel granularity. Set timeTravel.enabled=false to skip snapshots entirely.
    private boolean timeTravelEnabled = true;
    private final Set<TableStorage> dirtyTables = ConcurrentHashMap.newKeySet();

    public void setTimeTravelEnabled(final boolean enabled) { this.timeTravelEnabled = enabled; }
    public boolean isTimeTravelEnabled() { return timeTravelEnabled; }

    /** Record that a table was mutated by the current statement; it is snapshotted at statement end. */
    void markDirty(final TableStorage table) {
        if (timeTravelEnabled) {
            dirtyTables.add(table);
        }
    }

    /** Take one time-travel snapshot of every table mutated since the last call, then reset. */
    public void snapshotDirtyTables() {
        if (dirtyTables.isEmpty()) {
            return;
        }
        for (final TableStorage table : dirtyTables) {
            table.takeSnapshot();
        }
        dirtyTables.clear();
    }

    public void createTable(final String qualifiedName, final Table tableMetadata) {
        if (tables.containsKey(qualifiedName)) {
            throw new RuntimeException("Table storage already exists: " + qualifiedName);
        }
        tables.put(qualifiedName, new TableStorage(this, tableMetadata));
    }

    public void dropTable(final String qualifiedName) {
        if (!tables.containsKey(qualifiedName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        tables.remove(qualifiedName);
    }

    public boolean hasTable(final String qualifiedName) {
        return tables.containsKey(qualifiedName);
    }

    /**
     * Trade the rows a key answers with for the rows hidden under it, either of which may be absent — the
     * storage half of {@code Schema.swapShadow}.
     */
    public void swapShadow(final String qualifiedName) {
        final TableStorage visible = tables.remove(qualifiedName);
        final TableStorage hidden = shadowed.remove(qualifiedName);
        if (hidden != null) {
            tables.put(qualifiedName, hidden);
        }
        if (visible != null) {
            shadowed.put(qualifiedName, visible);
        }
    }

    /** Whether rows are hidden under that key, beneath a temporary table's. */
    public boolean hasShadowedTable(final String qualifiedName) {
        return shadowed.containsKey(qualifiedName);
    }

    /** The rows hidden under that key, beneath a temporary table's. */
    public TableStorage getShadowedTableStorage(final String qualifiedName) {
        final TableStorage storage = shadowed.get(qualifiedName);
        if (storage == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        return storage;
    }

    /** Create empty hidden rows under that key, for a permanent table restored beneath a temporary one. */
    public void createShadowedTable(final String qualifiedName, final Table tableMetadata) {
        if (shadowed.containsKey(qualifiedName)) {
            throw new RuntimeException("Table storage already exists: " + qualifiedName);
        }
        shadowed.put(qualifiedName, new TableStorage(this, tableMetadata));
    }

    /** Release the rows hidden under that key, if any. */
    public void dropShadowedTable(final String qualifiedName) {
        shadowed.remove(qualifiedName);
    }

    /** ALTER TABLE a SWAP WITH b: exchange the row storage of two tables (their structure is assumed compatible). */
    public void swapTables(final String fqNameA, final String fqNameB) {
        final TableStorage a = tables.get(fqNameA);
        final TableStorage b = tables.get(fqNameB);
        if (a == null || b == null) {
            throw new RuntimeException("Cannot SWAP: table storage missing for "
                + (a == null ? fqNameA : fqNameB));
        }
        tables.put(fqNameA, b);
        tables.put(fqNameB, a);
    }

    /** ALTER TABLE a RENAME TO b: move the row storage from the old key to the new one. */
    public void renameTable(final String oldQualifiedName, final String newQualifiedName) {
        final TableStorage storage = tables.remove(oldQualifiedName);
        if (storage != null) {
            tables.put(newQualifiedName, storage);
        }
    }

    /**
     * ALTER SCHEMA … RENAME TO: move every relation's rows of a renamed or moved schema to the keys its new
     * database and name give them.
     *
     * @param oldDatabase the schema's database before, canonical
     * @param oldSchema   the schema's name before, canonical
     * @param newDatabase its database after, canonical
     * @param newSchema   its name after, canonical
     */
    public void renameSchema(final String oldDatabase, final String oldSchema, final String newDatabase,
                             final String newSchema) {
        renameSchemaKeys(tables, oldDatabase, oldSchema, newDatabase, newSchema);
        renameSchemaKeys(shadowed, oldDatabase, oldSchema, newDatabase, newSchema);
    }

    /**
     * ALTER DATABASE … RENAME TO: move every relation's rows of the renamed database to the keys its new
     * name gives them.
     *
     * @param oldDatabase the database's name before, canonical
     * @param newDatabase its name after, canonical
     */
    public void renameDatabase(final String oldDatabase, final String newDatabase) {
        renameDatabaseKeys(tables, oldDatabase, newDatabase);
        renameDatabaseKeys(shadowed, oldDatabase, newDatabase);
    }

    private static void renameDatabaseKeys(final Map<String, TableStorage> stores, final String oldDatabase,
                                           final String newDatabase) {
        for (final String key : new ArrayList<String>(stores.keySet())) {
            final String[] parts = QualifiedName.parse(key).parts();
            if (parts.length == 3 && parts[0].equals(oldDatabase)) {
                stores.put(QualifiedName.key(newDatabase, parts[1], parts[2]), stores.remove(key));
            }
        }
    }

    private static void renameSchemaKeys(final Map<String, TableStorage> stores, final String oldDatabase,
                                         final String oldSchema, final String newDatabase, final String newSchema) {
        for (final String key : new ArrayList<String>(stores.keySet())) {
            final String[] parts = QualifiedName.parse(key).parts();
            if (parts.length == 3 && parts[0].equals(oldDatabase) && parts[1].equals(oldSchema)) {
                stores.put(QualifiedName.key(newDatabase, newSchema, parts[2]), stores.remove(key));
            }
        }
    }

    /**
     * How many rows a table holds as SHOW TABLES, SHOW OBJECTS and INFORMATION_SCHEMA.TABLES report it:
     * the rows stored under the key — the visible table's, or with {@code hidden} the permanent table a
     * temporary one of the same name hides — and 0 when nothing is stored there. A transaction's own
     * uncommitted writes wait in its write set rather than here, so they are not counted, which is what
     * live reports inside an open transaction too (live-verified).
     */
    public long storedRowCount(final String qualifiedName, final boolean hidden) {
        final TableStorage storage = hidden ? shadowed.get(qualifiedName) : tables.get(qualifiedName);
        return storage == null ? 0L : storage.getRowCount();
    }

    public TableStorage getTableStorage(final String qualifiedName) {
        final TableStorage storage = tables.get(qualifiedName);
        if (storage == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        return storage;
    }

    public void truncateTable(final String qualifiedName) {
        final TableStorage storage = getTableStorage(qualifiedName);
        storage.clear();
    }

    public void cloneTableData(final String sourceQualifiedName, final String targetQualifiedName) {
        final TableStorage sourceStorage = getTableStorage(sourceQualifiedName);
        final TableStorage targetStorage = getTableStorage(targetQualifiedName);

        // Copy all rows from source to target
        for (final Row row : sourceStorage.scan()) {
            // Create a new row with the same values
            final Row clonedRow = new Row(new ArrayList<>(row.getValues()));
            targetStorage.insert(clonedRow);
        }
    }
}
