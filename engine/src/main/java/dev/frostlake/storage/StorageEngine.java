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
import dev.frostlake.metastore.StatementSession;
import dev.frostlake.metastore.model.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class StorageEngine {

    private final Map<String, TableStorage> tables;
    /**
     * The rows of each PERMANENT table a TEMPORARY table of the same name hides, under that name's key.
     * The temporary table's rows take the key itself, so every read and write by name reaches them.
     */
    private final Map<String, TableStorage> shadowed;

    /**
     * Dropped or replaced tables' storage set aside for the open transactions that wrote them (see retireTable), and
     * dropped tables' storage waiting for an UNDROP (see keepDropped).
     */
    private final Map<String, TableStorage> retired = new ConcurrentHashMap<>();
    /** Numbers the keys a dropped table's storage waits under (see keepDropped). */
    private final AtomicLong droppedKeys = new AtomicLong();

    /**
     * The rows of each session's own temporary tables, by session and then by key (see Table.isSessionTemporary):
     * two sessions may each hold a temporary table of one name, and a session's temporary table above a permanent
     * one of its name leaves every other session reading the permanent rows (live-verified). Every read and write by
     * key reaches the current session's own rows first.
     */
    private final Map<Long, Map<String, TableStorage>> sessionTables = new ConcurrentHashMap<>();

    /** A session's own temporary table's rows, set aside while the session creates a permanent table of its name. */
    private final Map<Long, Map<String, TableStorage>> sessionTablesAside = new ConcurrentHashMap<>();
    // Package-private, not private: read by TableStorage, which is a top-level type in this
    // package rather than a nested one.
    boolean enforcePrimaryKey = false;
    boolean enforceUniqueKey = false;
    /**
     * The identity of the next row stored, whichever table it goes to. One sequence serves the whole engine, so a
     * row's id is never another row's: a stream holds it as METADATA$ROW_ID, and two tables swapped or a table
     * recreated under an old name must not hand its streams an id they already hold.
     */
    private final AtomicLong rowIds = new AtomicLong();

    public StorageEngine() {
        this.tables = new ConcurrentHashMap<>();
        this.shadowed = new ConcurrentHashMap<>();
    }

    /** The identity for a row about to be stored; see {@link #rowIds}. */
    long nextRowId() {
        return rowIds.getAndIncrement();
    }

    /**
     * The identity the next row stored will take, which a snapshot of the engine's state keeps.
     *
     * @return the next identity
     */
    public long nextRowIdentity() {
        return rowIds.get();
    }

    /**
     * Moves the identities on past one already taken — a restored row's, or the last a restored engine had drawn —
     * so no row stored later takes it.
     *
     * @param taken an identity some row holds
     */
    public void advanceRowIdsPast(final long taken) {
        long next = rowIds.get();
        while (next <= taken && !rowIds.compareAndSet(next, taken + 1)) {
            next = rowIds.get();
        }
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

    /** The tables written since {@link #takeWrittenRows} last asked, whatever the time-travel snapshots took. */
    private final Set<TableStorage> writtenTables = ConcurrentHashMap.newKeySet();

    /** Record that a table was mutated by the current statement; it is snapshotted at statement end. */
    void markDirty(final TableStorage table) {
        if (timeTravelEnabled) {
            dirtyTables.add(table);
        }
        writtenTables.add(table);
    }

    /**
     * The rows the tables written since the last call hold now, summed — what a statement that wrote them produced,
     * a table being rewritten whole (see the query history's ROWS_PRODUCED) — and start the record again. A write a
     * transaction buffers reaches no table until it commits.
     *
     * @return the rows
     */
    public long takeWrittenRows() {
        long total = 0;
        for (final TableStorage table : writtenTables) {
            total += table.getRowCount();
        }
        writtenTables.clear();
        return total;
    }

    /**
     * The tables written since the last call, and start the record again: set aside while a statement that another
     * one runs counts its own (see {@link #takeWrittenRows}).
     *
     * @return the tables
     */
    public Set<TableStorage> takeWrittenTables() {
        final Set<TableStorage> taken = new HashSet<>(writtenTables);
        writtenTables.clear();
        return taken;
    }

    /**
     * Put tables back on the record {@link #takeWrittenRows} reads.
     *
     * @param tables the tables
     */
    public void noteWrittenTables(final Set<TableStorage> tables) {
        writtenTables.addAll(tables);
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
        final Map<String, TableStorage> held = tableMetadata.isSessionTemporary()
            ? sessionMap(sessionTables, tableMetadata.getCreatedInSession()) : tables;
        if (held.containsKey(qualifiedName)) {
            throw new RuntimeException("Table storage already exists: " + qualifiedName);
        }
        held.put(qualifiedName, new TableStorage(this, tableMetadata));
    }

    public void dropTable(final String qualifiedName) {
        final Map<String, TableStorage> own = ownTables();
        if (own != null && own.remove(qualifiedName) != null) {
            return;
        }
        if (!tables.containsKey(qualifiedName)) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        tables.remove(qualifiedName);
    }

    public boolean hasTable(final String qualifiedName) {
        return isOwnSessionTable(qualifiedName) || tables.containsKey(qualifiedName)
            || retired.containsKey(qualifiedName) || anySessionTable(qualifiedName) != null;
    }

    /**
     * A session's own temporary table's rows under a key, read by the engine's own session, which reaches every
     * session's temporary tables; null in any other session.
     */
    private TableStorage anySessionTable(final String qualifiedName) {
        if (StatementSession.current() != null) {
            return null;
        }
        for (final Map<String, TableStorage> held : sessionTables.values()) {
            final TableStorage storage = held.get(qualifiedName);
            if (storage != null) {
                return storage;
            }
        }
        return null;
    }

    /**
     * Whether the key holds the current session's own temporary table: one no other session reaches, so no other
     * session's transaction can have written it or hold it locked.
     *
     * @param qualifiedName the table's key
     * @return true for the current session's own temporary table
     */
    public boolean isOwnSessionTable(final String qualifiedName) {
        final Map<String, TableStorage> own = ownTables();
        return own != null && own.containsKey(qualifiedName);
    }

    /**
     * Drop a session's own temporary table's rows, whichever session runs the statement: the session ended.
     *
     * @param qualifiedName the table's key
     * @param session       the session that created it
     */
    public void dropSessionTable(final String qualifiedName, final Long session) {
        final Map<String, TableStorage> held = sessionTables.get(session);
        if (held != null) {
            held.remove(qualifiedName);
            if (held.isEmpty()) {
                sessionTables.remove(session);
            }
        }
    }

    /**
     * Set the current session's own temporary table of a key aside while the session creates a permanent table of
     * that name, which every key then reaches; {@link #bringOwnTableBack} restores it.
     *
     * @param qualifiedName the key
     */
    public void setOwnTableAside(final String qualifiedName) {
        final Map<String, TableStorage> own = ownTables();
        final TableStorage storage = own == null ? null : own.remove(qualifiedName);
        if (storage != null) {
            sessionMap(sessionTablesAside, StatementSession.current()).put(qualifiedName, storage);
        }
    }

    /**
     * Bring the current session's own temporary table of a key back above the permanent table of its name.
     *
     * @param qualifiedName the key
     */
    public void bringOwnTableBack(final String qualifiedName) {
        final Long session = StatementSession.current();
        final Map<String, TableStorage> aside = session == null ? null : sessionTablesAside.get(session);
        final TableStorage storage = aside == null ? null : aside.remove(qualifiedName);
        if (storage != null) {
            sessionMap(sessionTables, session).put(qualifiedName, storage);
        }
    }

    /** The current session's own temporary tables' rows, or null for none (or the engine's own session). */
    private Map<String, TableStorage> ownTables() {
        final Long session = StatementSession.current();
        return session == null ? null : sessionTables.get(session);
    }

    /** A session's map in a per-session table, made when it has none. */
    private static Map<String, TableStorage> sessionMap(final Map<Long, Map<String, TableStorage>> bySession,
                                                        final Long session) {
        Map<String, TableStorage> held = bySession.get(session);
        if (held == null) {
            held = new ConcurrentHashMap<>();
            final Map<String, TableStorage> raced = bySession.putIfAbsent(session, held);
            if (raced != null) {
                held = raced;
            }
        }
        return held;
    }

    /**
     * Sets a dropped or replaced table's storage aside under a key no live table has: the open transactions of other
     * sessions that wrote it commit their changes there, to the table they wrote, and an UNDROP finds them there.
     *
     * @param qualifiedName the table's storage name
     * @param key           the key it is set aside under
     */
    public void retireTable(final String qualifiedName, final String key) {
        final TableStorage storage = tables.remove(qualifiedName);
        if (storage == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        retired.put(key, storage);
    }

    /**
     * Keeps a dropped table's storage, and the time-travel history it holds, under a key no live table has, for the
     * UNDROP that brings it back ({@link #restoreRetired}); the dropped version's purge lets it go
     * ({@link #discardRetired}). A session's own temporary table is dropped outright.
     *
     * @param qualifiedName the table's storage name
     * @return the key it is kept under, or null when it was dropped outright
     */
    public String keepDropped(final String qualifiedName) {
        if (isOwnSessionTable(qualifiedName) || !tables.containsKey(qualifiedName)) {
            dropTable(qualifiedName);
            return null;
        }
        final String key = qualifiedName + "#DROPPED_" + droppedKeys.incrementAndGet();
        retireTable(qualifiedName, key);
        return key;
    }

    /**
     * Storage for a dropped table a snapshot of the engine's state restored, kept as {@link #keepDropped} keeps a
     * dropped table's, to be filled with the rows and the history the snapshot saved.
     *
     * @param qualifiedName the table's storage name
     * @param tableMetadata the dropped table
     * @return the key it is kept under, which {@link #retiredStorage} reads
     */
    public String keepRestored(final String qualifiedName, final Table tableMetadata) {
        final String key = qualifiedName + "#DROPPED_" + droppedKeys.incrementAndGet();
        retired.put(key, new TableStorage(this, tableMetadata));
        return key;
    }

    /**
     * A dropped or set-aside table's storage by the key it waits under.
     *
     * @param key the key
     * @return the storage, or null when none waits under it
     */
    public TableStorage retiredStorage(final String key) {
        return retired.get(key);
    }

    /**
     * Lets a dropped table's kept storage go: its version was purged, so no UNDROP brings it back.
     *
     * @param key the key it was kept under
     */
    public void discardRetired(final String key) {
        retired.remove(key);
    }

    /**
     * Brings a set-aside table's storage back under its name, as an UNDROP does, with what the transactions that
     * wrote it committed meanwhile.
     *
     * @param key           the key it was set aside under
     * @param qualifiedName the name it comes back under
     * @return whether a set-aside storage was there to bring back
     */
    public boolean restoreRetired(final String key, final String qualifiedName) {
        final TableStorage storage = retired.remove(key);
        if (storage == null) {
            return false;
        }
        tables.put(qualifiedName, storage);
        return true;
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
        final Map<String, TableStorage> own = ownTables();
        if (own != null && own.containsKey(oldQualifiedName)) {
            own.put(newQualifiedName, own.remove(oldQualifiedName));
            return;
        }
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
        for (final Map<String, TableStorage> own : sessionTables.values()) {
            renameSchemaKeys(own, oldDatabase, oldSchema, newDatabase, newSchema);
        }
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
        for (final Map<String, TableStorage> own : sessionTables.values()) {
            renameDatabaseKeys(own, oldDatabase, newDatabase);
        }
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
        // A session's own temporary table hides the permanent table of its key from that session alone.
        final Map<String, TableStorage> own = ownTables();
        final TableStorage storage = own != null && own.containsKey(qualifiedName)
            ? hidden ? tables.get(qualifiedName) : own.get(qualifiedName)
            : hidden ? shadowed.get(qualifiedName) : tables.get(qualifiedName);
        return storage == null ? 0L : storage.getRowCount();
    }

    public TableStorage getTableStorage(final String qualifiedName) {
        final Map<String, TableStorage> own = ownTables();
        TableStorage storage = own == null ? null : own.get(qualifiedName);
        if (storage == null) {
            storage = tables.get(qualifiedName);
        }
        if (storage == null) {
            storage = anySessionTable(qualifiedName);
        }
        if (storage == null) {
            storage = retired.get(qualifiedName);
        }
        if (storage == null) {
            throw new RuntimeException(SqlCompilationError.doesNotExist("Table storage", qualifiedName));
        }
        return storage;
    }

    /**
     * A table's rows as every session shares them: the permanent or transient table under the key, never a session's
     * own temporary table of its name, which {@link #getTableStorage} reaches first. What a clone copies.
     *
     * @param qualifiedName the table's key
     * @return the rows, or null when no shared table holds the key
     */
    public TableStorage getSharedTableStorage(final String qualifiedName) {
        return tables.get(qualifiedName);
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
