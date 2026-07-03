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
import dev.frostlake.storage.StorageEngine.TableStorage;
import dev.frostlake.stream.StreamManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionManager {

    private static final Logger logger = LoggerFactory.getLogger(TransactionManager.class);

    private final AtomicLong transactionIdGenerator;
    private final Map<Long, Transaction> activeTransactions;
    private final ThreadLocal<Transaction> currentTransaction;
    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private StreamManager streamManager;
    private WalSink walSink;   // durability hook: receives a transaction's statements when it commits

    public TransactionManager(final Catalog catalog, final StorageEngine storageEngine) {
        this.transactionIdGenerator = new AtomicLong(1);
        this.activeTransactions = new ConcurrentHashMap<>();
        this.currentTransaction = new ThreadLocal<>();
        this.catalog = catalog;
        this.storageEngine = storageEngine;
    }

    /** Wire the engine's StreamManager so committed deferred changes emit CDC events at apply time. */
    public void setStreamManager(final StreamManager streamManager) {
        this.streamManager = streamManager;
    }

    /** Wire the engine's write-ahead log so a committed transaction's statements are made durable on commit
     *  (null when {@code durability.walEnabled} is off, in which case nothing is logged). */
    public void setWalSink(final WalSink walSink) {
        this.walSink = walSink;
    }

    public Transaction beginTransaction() {
        long txnId = transactionIdGenerator.getAndIncrement();
        Transaction txn = new Transaction(txnId, catalog, storageEngine, streamManager);
        activeTransactions.put(txnId, txn);
        currentTransaction.set(txn);
        return txn;
    }

    /** Begin (or promote the current auto-started) transaction to explicit — an explicit BEGIN suspends
     *  statement-end autocommit until COMMIT/ROLLBACK (Snowflake semantics). */
    public void beginExplicitTransaction() {
        Transaction txn = currentTransaction.get();
        if (txn == null) {
            txn = beginTransaction();
        }
        txn.setExplicit(true);
    }

    /** True if an explicit (BEGIN-started) transaction is currently active. */
    public boolean isExplicitTransaction() {
        final Transaction txn = currentTransaction.get();
        return txn != null && txn.isExplicit();
    }

    public void commit() {
        Transaction txn = currentTransaction.get();
        if (txn == null) {
            throw new RuntimeException("No active transaction");
        }

        if (txn.getState() != TransactionState.ACTIVE) {
            throw new RuntimeException("Transaction is not active");
        }

        // Captured before the txn is cleared below so the WAL append (after a successful apply) can use it.
        final List<String> walStatements = txn.getWalStatements();
        try {
            // Apply all changes
            txn.commit();
            txn.setState(TransactionState.COMMITTED);
        } catch (final Exception e) {
            txn.setState(TransactionState.FAILED);
            throw new RuntimeException("Failed to commit transaction", e);
        } finally {
            activeTransactions.remove(txn.getId());
            currentTransaction.remove();
        }

        // Durability: the commit has applied successfully — now append it to the WAL and fsync (a failure
        // here surfaces to the caller). Done after apply so the log only ever holds committed work.
        if (walSink != null && !walStatements.isEmpty()) {
            walSink.appendTransaction(walStatements);
        }
    }

    public void rollback() {
        Transaction txn = currentTransaction.get();
        if (txn == null) {
            throw new RuntimeException("No active transaction");
        }

        try {
            txn.rollback();
            txn.setState(TransactionState.ROLLED_BACK);
        } finally {
            activeTransactions.remove(txn.getId());
            currentTransaction.remove();
        }
    }

    // Snowflake has no SAVEPOINT statement, and the default deferred-apply path does not populate the
    // per-operation undo log a savepoint rollback would replay — so these silently did nothing rather
    // than partially rolling back. Reject them explicitly (consistent with supportsSavepoints() == false
    // and the JDBC savepoint methods, which also throw) instead of pretending they took effect.
    public void setSavepoint(final String name) {
        throw new UnsupportedOperationException("Savepoints are not supported");
    }

    public void rollbackToSavepoint(final String name) {
        throw new UnsupportedOperationException("Savepoints are not supported");
    }

    public void releaseSavepoint(final String name) {
        throw new UnsupportedOperationException("Savepoints are not supported");
    }

    public Transaction getCurrentTransaction() {
        return currentTransaction.get();
    }

    public boolean hasActiveTransaction() {
        return currentTransaction.get() != null;
    }

    public void setCurrentTransaction(final Long transactionId) {
        if (transactionId == null) {
            currentTransaction.remove();
        } else {
            Transaction txn = activeTransactions.get(transactionId);
            if (txn != null) {
                currentTransaction.set(txn);
            }
        }
    }

    public Collection<Transaction> getActiveTransactions() {
        return activeTransactions.values();
    }

    public Long getCurrentTransactionId() {
        Transaction txn = currentTransaction.get();
        return txn != null ? txn.getId() : null;
    }

    public static class Transaction {

        private final long id;
        private TransactionState state;
        private final List<TransactionLog> logs;
        private final TransactionWriteSet writeSet = new TransactionWriteSet();   // deferred-apply buffer (Phase 1)
        private final List<String> walStatements = new ArrayList<>();   // mutating SQL to log on commit (WAL)
        private final Set<Stream> streamsToConsume = new LinkedHashSet<>();   // CDC streams read by this txn's DML
        private final long startTime;
        private final Map<String, Integer> savepoints;
        private final Catalog catalog;
        private final StorageEngine storageEngine;
        private final StreamManager streamManager;
        private boolean explicit;   // started by an explicit BEGIN → suspends statement-end autocommit

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

        /** Record a mutating statement to be written to the WAL (as part of this transaction) on commit. */
        public void logStatementForWal(final String sql) {
            walStatements.add(sql);
        }

        /** The mutating statements buffered for the WAL, in execution order (empty when the WAL is off). */
        public List<String> getWalStatements() {
            return walStatements;
        }

        /** Register a stream read by a consuming DML in this txn; consumed on commit, discarded on rollback. */
        public void registerStreamConsumption(final Stream stream) {
            streamsToConsume.add(stream);
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
        }

        public void logUpdate(final String tableName, final int rowIndex, final Row oldRow, final Row newRow) {
            logs.add(new UpdateLog(tableName, rowIndex, oldRow, newRow));
        }

        public void logDelete(final String tableName, final int rowIndex, final Row row) {
            logs.add(new DeleteLog(tableName, rowIndex, row));
        }

        public void commit() {
            // Deferred-apply path: flush buffered writes to storage now (atomically), emit stream CDC at
            // apply time, then snapshot for time travel — so committed state (not per-DML state) is what
            // streams and time-travel observe. The immediate path leaves the write set empty (nothing here).
            if (!writeSet.isEmpty()) {
                writeSet.applyTo(storageEngine, streamManager);
                storageEngine.snapshotDirtyTables();
            }
            // Snowflake: a stream used as a source in a committed DML advances its offset on commit.
            for (final Stream stream : streamsToConsume) {
                stream.consume();
            }
            streamsToConsume.clear();
            logs.clear();
            writeSet.clear();
            savepoints.clear();
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
            Integer logIndex = savepoints.get(name);
            if (logIndex == null) {
                throw new RuntimeException("Savepoint not found: " + name);
            }

            // Undo all changes after the savepoint (in reverse order)
            for (int i = logs.size() - 1; i >= logIndex; i--) {
                TransactionLog log = logs.get(i);
                undoOperation(log);
            }

            // Remove all logs after the savepoint
            while (logs.size() > logIndex) {
                logs.remove(logs.size() - 1);
            }

            // Remove savepoints created AFTER this one (not including this one)
            List<String> toRemove = new ArrayList<>();
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
                InsertLog insertLog = (InsertLog) log;
                try {
                    TableStorage storage = storageEngine.getTableStorage(insertLog.getTableName());
                    // The row was inserted at this index, so delete it
                    // Note: if multiple operations occurred, indices may have shifted
                    // For now, we'll delete at the logged index
                    int rowIndex = insertLog.getRowIndex();
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
                UpdateLog updateLog = (UpdateLog) log;
                try {
                    TableStorage storage = storageEngine.getTableStorage(updateLog.getTableName());
                    int rowIndex = updateLog.getRowIndex();
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
                DeleteLog deleteLog = (DeleteLog) log;
                try {
                    TableStorage storage = storageEngine.getTableStorage(deleteLog.getTableName());
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
            Integer logIndex = savepoints.get(name);
            if (logIndex == null) {
                throw new RuntimeException("Savepoint not found: " + name);
            }

            // Remove this savepoint and all savepoints created after it
            List<String> toRemove = new ArrayList<>();
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

    private abstract static class TransactionLog {
        protected final String tableName;

        protected TransactionLog(final String tableName) {
            this.tableName = tableName;
        }

        public String getTableName() {
            return tableName;
        }
    }

    private static class InsertLog extends TransactionLog {
        private final int rowIndex;
        private final Row row;

        public InsertLog(final String tableName, final int rowIndex, final Row row) {
            super(tableName);
            this.rowIndex = rowIndex;
            this.row = row;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public Row getRow() {
            return row;
        }
    }

    private static class UpdateLog extends TransactionLog {
        private final int rowIndex;
        private final Row oldRow;
        private final Row newRow;

        public UpdateLog(final String tableName, final int rowIndex, final Row oldRow, final Row newRow) {
            super(tableName);
            this.rowIndex = rowIndex;
            this.oldRow = oldRow;
            this.newRow = newRow;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public Row getOldRow() {
            return oldRow;
        }

        public Row getNewRow() {
            return newRow;
        }
    }

    private static class DeleteLog extends TransactionLog {
        private final int rowIndex;
        private final Row row;

        public DeleteLog(final String tableName, final int rowIndex, final Row row) {
            super(tableName);
            this.rowIndex = rowIndex;
            this.row = row;
        }

        public int getRowIndex() {
            return rowIndex;
        }

        public Row getRow() {
            return row;
        }
    }
}
