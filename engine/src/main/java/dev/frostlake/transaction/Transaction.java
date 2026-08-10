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

package dev.frostlake.transaction;

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.stream.StreamManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transaction {

    private static final Logger logger = LoggerFactory.getLogger(Transaction.class);

    private final long id;
    private TransactionState state;
    private final List<TransactionLog> logs;
    private final TransactionWriteSet writeSet = new TransactionWriteSet();   // deferred-apply buffer (Phase 1)
    private final List<String> walStatements = new ArrayList<>();   // mutating SQL to log on commit (WAL)
    /** What each buffered statement called "now" — one per entry of walStatements, same order. */
    private final List<Instant> walInstants = new ArrayList<>();
    // CDC streams read by this txn's DML, each with the scope of what the read SAW (committed cut +
    // this txn's then-buffered changes) so commit advances past exactly that. Latest read governs.
    private final Map<Stream, StreamReadScope> streamsToConsume = new LinkedHashMap<>();
    private final long startTime;
    private final Map<String, Integer> savepoints;
    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private final StreamManager streamManager;
    private boolean explicit;   // started by an explicit BEGIN → suspends statement-end autocommit
    /** SHOW TRANSACTIONS' name cell — live names an unnamed transaction with a fresh UUID, and a
     *  {@code BEGIN NAME <name>} replaces it with that name. */
    private String name = UUID.randomUUID().toString();
    /** Tables this txn's IMMEDIATE-apply partition-rewriting DML touched; the deferred path
     *  tracks its own set in {@link TransactionWriteSet}. One {@code PARTITIONS} lock per table
     *  (live-verified — appends do NOT lock), released with the transaction. */
    private final Map<String, TableLock> tableLocks = new LinkedHashMap<>();

    public Transaction(final long id, final Catalog catalog, final StorageEngine storageEngine,
                       final StreamManager streamManager) {
        this.id = id;
        this.state = TransactionState.ACTIVE;
        this.logs = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
        this.savepoints = new HashMap<>();
        this.catalog = catalog;
        this.storageEngine = storageEngine;
        this.streamManager = streamManager;
    }

    public long getId() {
        return id;
    }

    public TransactionState getState() {
        return state;
    }

    public void setState(final TransactionState state) {
        this.state = state;
    }

    /** This transaction's deferred-apply write set (stays empty unless transaction.deferredApply is on). */
    public TransactionWriteSet getWriteSet() {
        return writeSet;
    }

    /**
     * Record a mutating statement to be written to the WAL (as part of this transaction) on commit,
     * together with the instant it called "now" — statements of one transaction get DIFFERENT
     * instants, so the log keeps one per statement rather than one per record.
     */
    public void logStatementForWal(final String sql, final Instant instant) {
        walStatements.add(sql);
        walInstants.add(instant);
    }

    /** The mutating statements buffered for the WAL, in execution order (empty when the WAL is off). */
    public List<String> getWalStatements() {
        return walStatements;
    }

    /** The instants of {@link #getWalStatements}, in the same order. */
    public List<Instant> getWalInstants() {
        return walInstants;
    }

    /**
     * Register a stream read by a consuming DML in this txn; consumed on commit (scoped to what the
     * read saw — see {@link StreamReadScope}), discarded on rollback. A re-read replaces the scope:
     * the latest read saw the most, and both parts of the scope only ever grow within a transaction.
     */
    public void registerStreamConsumption(final Stream stream, final StreamReadScope scope) {
        streamsToConsume.put(stream, scope);
    }

    /** True if started by an explicit BEGIN — statement-end autocommit is suspended until COMMIT/ROLLBACK. */
    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(final boolean explicit) {
        this.explicit = explicit;
    }

    public void logInsert(final String tableName, final int rowIndex, final Row row) {
        logs.add(new InsertLog(tableName, rowIndex, row));
        // No lock registration: an append-only INSERT holds no PARTITIONS lock (live-verified).
    }

    public void logUpdate(final String tableName, final int rowIndex, final Row oldRow, final Row newRow) {
        logs.add(new UpdateLog(tableName, rowIndex, oldRow, newRow));
        recordTableTouch(tableName);
    }

    public void logDelete(final String tableName, final int rowIndex, final Row row) {
        logs.add(new DeleteLog(tableName, rowIndex, row));
        recordTableTouch(tableName);
    }

    /** The transaction's name, as SHOW TRANSACTIONS reports it: the one BEGIN NAME gave it, or the
     *  system-generated UUID when it was opened without one. */
    public String getName() {
        return name;
    }

    /** Name this transaction — {@code BEGIN NAME <name>} / {@code START TRANSACTION NAME <name>}. */
    public void setName(final String name) {
        this.name = name;
    }

    /** Register the PARTITIONS lock a DML touch takes on a table; the first touch wins the cell. */
    public void recordTableTouch(final String tableName) {
        final String key = tableName.toUpperCase();
        if (!tableLocks.containsKey(key)) {
            tableLocks.put(key, new TableLock(System.currentTimeMillis(), UUID.randomUUID().toString()));
        }
    }

    /** Every table lock this transaction holds, whichever apply path recorded it. */
    public Map<String, TableLock> getAllTableLocks() {
        if (writeSet.getTableLocks().isEmpty()) {
            return tableLocks;
        }
        final Map<String, TableLock> merged = new LinkedHashMap<>(tableLocks);
        merged.putAll(writeSet.getTableLocks());
        return merged;
    }

    public void commit() {
        // Deferred-apply path: flush buffered writes to storage now (atomically), emit stream CDC at
        // apply time, then snapshot for time travel — so committed state (not per-DML state) is what
        // streams and time-travel observe. The immediate path leaves the write set empty (nothing here).
        if (!writeSet.isEmpty()) {
            writeSet.applyTo(storageEngine, streamManager);
            storageEngine.snapshotDirtyTables();
        }
        // Snowflake: a stream used as a source in a committed DML advances its offset on commit —
        // but only past what the consuming read actually saw (the write set just re-emitted the
        // txn's buffered changes as real records; changes buffered AFTER the read stay unconsumed).
        for (final Map.Entry<Stream, StreamReadScope> entry : streamsToConsume.entrySet()) {
            entry.getKey().consumeSeen(entry.getValue().getCommittedCut(), entry.getValue().getSeenTransient());
        }
        streamsToConsume.clear();
        logs.clear();
        writeSet.clear();
        savepoints.clear();
        tableLocks.clear();
    }

    public void rollback() {
        if (writeSet.isEmpty()) {
            // Legacy immediate-apply path: undo each logged operation in reverse.
            for (int i = logs.size() - 1; i >= 0; i--) {
                undoOperation(logs.get(i));
            }
        }
        // Deferred-apply path: nothing was written to storage, so discarding the write set IS the
        // rollback (the clear below). Streams read by this txn's DML are NOT consumed on rollback.
        streamsToConsume.clear();
        logs.clear();
        writeSet.clear();
        savepoints.clear();
        tableLocks.clear();
    }

    public long getStartTime() {
        return startTime;
    }

    public int getLogCount() {
        return logs.size();
    }

    public void createSavepoint(final String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Savepoint name cannot be null or empty");
        }
        // Store the current log size as the savepoint
        savepoints.put(name, logs.size());
    }

    public void rollbackToSavepoint(final String name) {
        final Integer logIndex = savepoints.get(name);
        if (logIndex == null) {
            throw new RuntimeException("Savepoint not found: " + name);
        }

        // Undo all changes after the savepoint (in reverse order)
        for (int i = logs.size() - 1; i >= logIndex; i--) {
            final TransactionLog log = logs.get(i);
            undoOperation(log);
        }

        // Remove all logs after the savepoint
        while (logs.size() > logIndex) {
            logs.remove(logs.size() - 1);
        }

        // Remove savepoints created AFTER this one (not including this one)
        final List<String> toRemove = new ArrayList<>();
        for (final Map.Entry<String, Integer> entry : savepoints.entrySet()) {
            if (entry.getValue() > logIndex) {
                toRemove.add(entry.getKey());
            }
        }
        for (final String sp : toRemove) {
            savepoints.remove(sp);
        }
    }

    private void undoOperation(final TransactionLog log) {
        if (log instanceof InsertLog) {
            // Undo INSERT: delete the row at the logged index
            final InsertLog insertLog = (InsertLog) log;
            try {
                final TableStorage storage = storageEngine.getTableStorage(insertLog.getTableName());
                // The row was inserted at this index, so delete it
                // Note: if multiple operations occurred, indices may have shifted
                // For now, we'll delete at the logged index
                final int rowIndex = insertLog.getRowIndex();
                if (rowIndex >= 0 && rowIndex < storage.getRowCount()) {
                    storage.delete(rowIndex);
                }
            } catch (final Exception e) {
                // Continue undoing the remaining operations, but log it — a rollback-undo failure can
                // leave inconsistent state and must not be silently swallowed.
                logger.warn("Error undoing a logged operation during rollback: {}", e.getMessage(), e);
            }
        } else if (log instanceof UpdateLog) {
            // Undo UPDATE: restore the old row
            final UpdateLog updateLog = (UpdateLog) log;
            try {
                final TableStorage storage = storageEngine.getTableStorage(updateLog.getTableName());
                final int rowIndex = updateLog.getRowIndex();
                if (rowIndex >= 0 && rowIndex < storage.getRowCount()) {
                    storage.update(rowIndex, updateLog.getOldRow());
                }
            } catch (final Exception e) {
                // Continue undoing the remaining operations, but log it — a rollback-undo failure can
                // leave inconsistent state and must not be silently swallowed.
                logger.warn("Error undoing a logged operation during rollback: {}", e.getMessage(), e);
            }
        } else if (log instanceof DeleteLog) {
            // Undo DELETE: reinsert the row at the original position
            final DeleteLog deleteLog = (DeleteLog) log;
            try {
                final TableStorage storage = storageEngine.getTableStorage(deleteLog.getTableName());
                // Need to insert at original position
                // But TableStorage.insert() doesn't support specifying an index
                // We'll need to directly access the internal list
                // For now, just insert at the end
                storage.insert(deleteLog.getRow());
            } catch (final Exception e) {
                // Continue undoing the remaining operations, but log it — a rollback-undo failure can
                // leave inconsistent state and must not be silently swallowed.
                logger.warn("Error undoing a logged operation during rollback: {}", e.getMessage(), e);
            }
        }
    }

    public void releaseSavepoint(final String name) {
        final Integer logIndex = savepoints.get(name);
        if (logIndex == null) {
            throw new RuntimeException("Savepoint not found: " + name);
        }

        // Remove this savepoint and all savepoints created after it
        final List<String> toRemove = new ArrayList<>();
        for (final Map.Entry<String, Integer> entry : savepoints.entrySet()) {
            if (entry.getValue() >= logIndex) {
                toRemove.add(entry.getKey());
            }
        }
        for (final String sp : toRemove) {
            savepoints.remove(sp);
        }
    }
}
