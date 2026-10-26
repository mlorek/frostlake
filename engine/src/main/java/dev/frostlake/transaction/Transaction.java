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

import dev.frostlake.executor.StatementCancellation;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.metastore.model.Stream;
import dev.frostlake.storage.Row;
import dev.frostlake.storage.SnapshotSequence;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.storage.TableStorage;
import dev.frostlake.stream.StreamManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    // The statements run inside this transaction, whose changes become visible when it commits.
    private final List<QueryHistory> statements = new ArrayList<>();
    /** Each statement that ran here under a savepoint, by identity, with its ordinal among them. */
    private final Map<QueryHistory, Long> ordinals = new IdentityHashMap<>();
    /** What each buffered statement called "now" — one per entry of walStatements, same order. */
    private final List<Instant> walInstants = new ArrayList<>();
    /** What each buffered statement ran under — one per entry of walStatements, same order. */
    private final List<WalStatementContext> walContexts = new ArrayList<>();

    /** What the transaction's statements left for its end, in the order they registered it. */
    private final List<TransactionOutcome> outcomes = new ArrayList<>();
    // CDC streams read by this txn's DML, each with the point its reads end at - the transaction's start - so commit
    // advances the stream to exactly that.
    private final Map<Stream, StreamReadScope> streamsToConsume = new LinkedHashMap<>();
    private final long startTime;
    /**
     * Where the transaction began among the snapshot numbers: its stream reads list the changes captured before it,
     * and its commit leaves each stream it consumed at this point (live-verified).
     */
    private final long startSequence;
    private final Map<String, Integer> savepoints;
    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private final StreamManager streamManager;
    private boolean explicit;   // started by an explicit BEGIN → suspends statement-end autocommit
    /** Whether a statement of an autocommit session began the transaction, to end with that statement. */
    private boolean statementScoped;
    /** The user whose SYSTEM$ABORT_TRANSACTION ended the transaction, or null while nobody has. */
    private volatile String abortedBy;
    private boolean begunInBlock;
    private int scope;
    private Transaction outer;
    /** SHOW TRANSACTIONS' name cell — live names an unnamed transaction with a fresh UUID, and a
     *  {@code BEGIN NAME <name>} replaces it with that name. */
    private String name = UUID.randomUUID().toString();
    /** Tables this txn's DML touched, and those its IMMEDIATE-apply writes wrote; the deferred path
     *  tracks the tables it writes in {@link TransactionWriteSet}. One {@code PARTITIONS} lock per
     *  table, released with the transaction (see {@link TableLock}). */
    private final Map<String, TableLock> tableLocks = new LinkedHashMap<>();
    /** Whether this transaction's IMMEDIATE-apply INSERTs into a table take its lock, by table upper-cased: decided
     *  at its first INSERT there, by whether the table held rows then. */
    private final Map<String, Boolean> insertLocks = new HashMap<>();

    /** The streams this transaction's reads hold against other sessions' reads, each with its lock (see StreamLock). */
    private final Map<Stream, StreamLock> streamLocks = new LinkedHashMap<>();

    /** The table a statement of this transaction waits for, by its storage name upper-cased, or null. */
    private volatile String waitingFor;
    /** Whether the lock that statement waits for is a stream's. */
    private volatile boolean waitingForStream;

    /** The query ID of the statement that waits, which SHOW LOCKS and its refusal name. */
    private volatile String waitingQueryId;
    /** The number of the session that began the transaction — SHOW TRANSACTIONS' and SHOW LOCKS'
     *  session cell — or null for a transaction begun with no session context wired. */
    private volatile Long sessionNumber;

    public Transaction(final long id, final Catalog catalog, final StorageEngine storageEngine,
                       final StreamManager streamManager) {
        this.id = id;
        this.state = TransactionState.ACTIVE;
        this.logs = new ArrayList<>();
        this.startTime = System.currentTimeMillis();
        this.startSequence = SnapshotSequence.mark();
        this.savepoints = new HashMap<>();
        this.catalog = catalog;
        this.storageEngine = storageEngine;
        this.streamManager = streamManager;
    }

    public long getId() {
        return id;
    }

    /**
     * The transaction's one public id: the 19-digit number CURRENT_TRANSACTION() and LAST_TRANSACTION()
     * answer and SHOW TRANSACTIONS and SHOW LOCKS list — its start instant in epoch nanoseconds, with the
     * engine's counter in the low digits so two transactions begun in the same millisecond stay apart.
     * {@link #getId()} is the engine's internal counter and is never shown.
     *
     * @return the id every SQL surface prints
     */
    /** Record a statement that ran inside this transaction, so its commit can date the statement's changes. */
    public void addStatement(final QueryHistory statement) {
        statements.add(statement);
        noteOrdinal(statement);
    }

    /**
     * Note where a statement falls among this transaction's statements, whether it succeeded or failed: what a
     * time-travel read naming it, while the transaction is still open, reads the transaction's own changes up to
     * (see {@link TransactionWriteSet#overlayRowsAsOf}).
     *
     * @param statement the statement, as it ends
     */
    public void noteOrdinal(final QueryHistory statement) {
        final long ordinal = writeSet.activeOrdinal();
        if (ordinal >= 0 && !ordinals.containsKey(statement)) {
            ordinals.put(statement, Long.valueOf(ordinal));
        }
    }

    /**
     * Where a statement falls among this transaction's statements.
     *
     * @param statement a statement
     * @return its ordinal, or -1 when it did not run in this transaction or ran under no savepoint
     */
    public long ordinalOf(final QueryHistory statement) {
        final Long ordinal = statement == null ? null : ordinals.get(statement);
        return ordinal == null ? -1L : ordinal.longValue();
    }

    /**
     * The ordinal of the last of this transaction's statements that had ended by an instant.
     *
     * @param instant  the instant
     * @param strictly whether the statement must have ended before the instant rather than by it
     * @return the ordinal, or -1 when none had
     */
    public long lastOrdinalEndedBy(final LocalDateTime instant, final boolean strictly) {
        long last = -1L;
        for (final Map.Entry<QueryHistory, Long> noted : ordinals.entrySet()) {
            final LocalDateTime ended = noted.getKey().getEndTime();
            if (ended != null && (strictly ? ended.isBefore(instant) : !ended.isAfter(instant))) {
                last = Math.max(last, noted.getValue().longValue());
            }
        }
        return last;
    }

    /** The statements that ran inside this transaction, in the order they ran. */
    public List<QueryHistory> getStatements() {
        return statements;
    }

    public long getPublicId() {
        return startTime * 1_000_000L + id % 1_000_000L;
    }

    /** The number of the session that began this transaction, or null when none was known. */
    public Long getSessionNumber() {
        return sessionNumber;
    }

    /** Record the session that began this transaction. */
    public void setSessionNumber(final Long sessionNumber) {
        this.sessionNumber = sessionNumber;
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
     * instants, so the log keeps one per statement rather than one per record — and what it ran under, or null
     * where nothing was noted.
     */
    public void logStatementForWal(final String sql, final Instant instant, final WalStatementContext context) {
        walStatements.add(sql);
        walInstants.add(instant);
        walContexts.add(context);
    }

    /** The mutating statements buffered for the WAL, in execution order (empty when the WAL is off). */
    public List<String> getWalStatements() {
        return walStatements;
    }

    /** The instants of {@link #getWalStatements}, in the same order. */
    public List<Instant> getWalInstants() {
        return walInstants;
    }

    /** What the statements of {@link #getWalStatements} ran under, in the same order. */
    public List<WalStatementContext> getWalContexts() {
        return walContexts;
    }

    /**
     * Register a stream read by a consuming DML in this txn; consumed on commit up to the point the read ended at
     * (see {@link StreamReadScope}), discarded on rollback. Every read of the stream in the transaction ends at the
     * same point, so a re-read replaces the scope with its equal.
     */
    public void registerStreamConsumption(final Stream stream, final StreamReadScope scope) {
        streamsToConsume.put(stream, scope);
    }

    /**
     * Begin a statement's savepoint ({@link TransactionWriteSet#beginStatementSavepoint}), the streams the
     * transaction's consuming reads registered so far noted beside it.
     *
     * @return the savepoint
     */
    public WriteSetSavepoint beginStatementSavepoint() {
        final WriteSetSavepoint savepoint = writeSet.beginStatementSavepoint();
        savepoint.noteStreamReads(new LinkedHashMap<>(streamsToConsume));
        savepoint.noteOutcomes(outcomes.size());
        return savepoint;
    }

    /**
     * Leaves work for the transaction's end: run when it commits, its writes applied, or undone when it rolls back
     * or the statement that registered it fails inside it.
     *
     * @param outcome the work
     */
    public void onOutcome(final TransactionOutcome outcome) {
        outcomes.add(outcome);
    }

    /**
     * Undo a failed statement ({@link TransactionWriteSet#rollbackToSavepoint}), the stream reads it registered
     * included: "the changes made by that failed statement are rolled back", and a stream advances at COMMIT only
     * for a statement that consumed it (live-verified: a failed INSERT … SELECT FROM a stream leaves the stream
     * whole after the transaction commits).
     *
     * @param savepoint what {@link #beginStatementSavepoint} returned for the statement
     */
    public void rollbackToSavepoint(final WriteSetSavepoint savepoint) {
        if (writeSet.rollbackToSavepoint(savepoint)) {
            if (savepoint.streamReadsBefore() != null) {
                streamsToConsume.clear();
                streamsToConsume.putAll(savepoint.streamReadsBefore());
            }
            if (savepoint.outcomesBefore() >= 0) {
                undoOutcomesFrom(savepoint.outcomesBefore());
            }
        }
    }

    /** Undoes the outcomes registered from an index on, the latest first. */
    private void undoOutcomesFrom(final int first) {
        for (int i = outcomes.size() - 1; i >= first; i--) {
            outcomes.remove(i).rolledBack();
        }
    }

    /** True if started by an explicit BEGIN — statement-end autocommit is suspended until COMMIT/ROLLBACK. */
    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(final boolean explicit) {
        this.explicit = explicit;
    }

    /**
     * Whether the transaction ends with the statement that began it: one a statement of an autocommit session
     * began, which no BEGIN has made explicit since.
     */
    public boolean isStatementScoped() {
        return statementScoped && !explicit;
    }

    public void setStatementScoped(final boolean statementScoped) {
        this.statementScoped = statementScoped;
    }

    /** The user whose SYSTEM$ABORT_TRANSACTION ended the transaction, or null while nobody has. */
    public String getAbortedBy() {
        return abortedBy;
    }

    public void setAbortedBy(final String abortedBy) {
        this.abortedBy = abortedBy;
    }

    /**
     * Whether a Snowflake Scripting block or a procedure began the transaction: it is then that block's to end, and
     * one ended from inside the block leaves nothing for the session to acknowledge.
     */
    public boolean isBegunInBlock() {
        return begunInBlock;
    }

    public void setBegunInBlock(final boolean begunInBlock) {
        this.begunInBlock = begunInBlock;
    }

    /**
     * The scope whose BEGIN made the transaction explicit, as {@link TransactionManager#enterScope} counts them:
     * 0 for a statement of the session's own, one more inside each block statement, procedure body and text that
     * is a block. It is that scope's to end.
     */
    public int getScope() {
        return scope;
    }

    public void setScope(final int scope) {
        this.scope = scope;
    }

    /**
     * The transaction of an enclosing scope this one was begun beside: it waits, untouched, until this one ends,
     * and is the session's again then. Null for a transaction begun with none open.
     */
    public Transaction getOuter() {
        return outer;
    }

    public void setOuter(final Transaction outer) {
        this.outer = outer;
    }

    public void logInsert(final String tableName, final int rowIndex, final Row row) {
        logs.add(new InsertLog(tableName, rowIndex, row));
        // An INSERT takes the table's lock where the table holds rows, and none on an empty one (live-verified).
        final String key = tableName.toUpperCase(Locale.ROOT);
        Boolean locks = insertLocks.get(key);
        if (locks == null) {
            locks = Boolean.valueOf(rowIndex > 0);
            insertLocks.put(key, locks);
        }
        if (locks.booleanValue()) {
            recordTableWrite(tableName);
        }
    }

    public void logUpdate(final String tableName, final int rowIndex, final Row oldRow, final Row newRow) {
        logs.add(new UpdateLog(tableName, rowIndex, oldRow, newRow));
        recordTableWrite(tableName);
    }

    public void logDelete(final String tableName, final int rowIndex, final Row row) {
        logs.add(new DeleteLog(tableName, rowIndex, row));
        recordTableWrite(tableName);
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

    /**
     * Whether this transaction has written a row of a table — inserted, changed or removed one — and so holds the
     * table against other sessions' writers (see {@link TableLockGate}). Read by the transaction's own session, or
     * under the engine's lock.
     *
     * @param fullyQualifiedName the table's storage name
     * @return whether the transaction has written the table
     */
    public boolean hasWritten(final String fullyQualifiedName) {
        for (final String changed : writeSet.changedTables()) {
            if (changed.equalsIgnoreCase(fullyQualifiedName)) {
                return true;
            }
        }
        for (final TransactionLog log : logs) {
            if (fullyQualifiedName.equalsIgnoreCase(log.getTableName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The statement a lock refusal names as holding a table: the latest of this transaction's statements that
     * changed a row of it (live-verified).
     *
     * @param fullyQualifiedName the table's storage name
     * @return the statement's query ID
     */
    public synchronized String lockQueryId(final String fullyQualifiedName) {
        final TableLock lock = getAllTableLocks().get(fullyQualifiedName.toUpperCase(Locale.ROOT));
        return lock != null ? lock.getQueryId() : UUID.randomUUID().toString();
    }

    /**
     * A statement of this transaction waits for another transaction's lock on a table: SHOW LOCKS lists the lock as
     * WAITING, with the statement's query ID, until the statement takes the lock or gives up (live-verified).
     *
     * @param fullyQualifiedName the table's storage name
     * @param queryId            the waiting statement's query ID
     */
    public void beginWaiting(final String fullyQualifiedName, final String queryId) {
        beginWaiting(fullyQualifiedName, queryId, false);
    }

    /**
     * A statement of this transaction waits for another transaction's lock on a table or a stream.
     *
     * @param resource the table's storage name or the stream's full name
     * @param queryId  the waiting statement's query ID
     * @param stream   whether the lock is a stream's
     */
    public void beginWaiting(final String resource, final String queryId, final boolean stream) {
        waitingQueryId = queryId;
        waitingForStream = stream;
        waitingFor = resource.toUpperCase(Locale.ROOT);
    }

    /** The waiting statement took its lock or gave up. */
    public void endWaiting() {
        waitingFor = null;
        waitingQueryId = null;
        waitingForStream = false;
    }

    /** Whether the lock a statement of this transaction waits for is a stream's. */
    public boolean isWaitingForStream() {
        return waitingForStream;
    }

    /**
     * Takes the lock on a stream this transaction reads, unless it holds it already: the first read names it.
     *
     * @param stream   the stream
     * @param resource its full name, as SHOW LOCKS lists it
     */
    public synchronized void lockStream(final Stream stream, final String resource) {
        if (!streamLocks.containsKey(stream)) {
            streamLocks.put(stream, new StreamLock(resource, System.currentTimeMillis(), runningStatementId()));
        }
    }

    /**
     * Whether this transaction holds a stream's lock.
     *
     * @param stream the stream
     * @return whether a read of this transaction took it
     */
    public synchronized boolean holdsStreamLock(final Stream stream) {
        return streamLocks.containsKey(stream);
    }

    /**
     * The statement a lock refusal names as holding a stream: the one that first read it.
     *
     * @param stream the stream
     * @return the statement's query ID
     */
    public synchronized String streamLockQueryId(final Stream stream) {
        final StreamLock lock = streamLocks.get(stream);
        return lock != null ? lock.getQueryId() : UUID.randomUUID().toString();
    }

    /** Every stream lock this transaction holds, in the order its reads took them. */
    public synchronized List<StreamLock> getStreamLocks() {
        return new ArrayList<>(streamLocks.values());
    }

    /** The table a statement of this transaction waits for, upper-cased, or null when none waits. */
    public String getWaitingFor() {
        return waitingFor;
    }

    /** The query ID of the statement that waits, or null when none waits. */
    public String getWaitingQueryId() {
        return waitingQueryId;
    }

    /**
     * Register the PARTITIONS lock a DML statement takes on a table whether or not it changes a row: the first
     * touch wins the cell, named by the statement that touched it.
     *
     * @param tableName the table's storage name
     */
    public void recordTableTouch(final String tableName) {
        final String key = tableName.toUpperCase(Locale.ROOT);
        if (!tableLocks.containsKey(key)) {
            tableLocks.put(key, new TableLock(System.currentTimeMillis(), runningStatementId()));
        }
    }

    /**
     * Register the lock an immediate-apply write of a row takes on a table: acquired at the table's first touch, and
     * named by the latest statement that changed a row of it.
     */
    private void recordTableWrite(final String tableName) {
        final String key = tableName.toUpperCase(Locale.ROOT);
        final TableLock held = tableLocks.get(key);
        final String writer = StatementCancellation.runningQueryId();
        if (held == null) {
            tableLocks.put(key, new TableLock(System.currentTimeMillis(), writer != null ? writer
                : UUID.randomUUID().toString()));
        } else if (writer != null && !writer.equals(held.getQueryId())) {
            tableLocks.put(key, new TableLock(held.getAcquiredOn(), writer));
        }
    }

    /** The query ID of the statement running now, or a fresh one for a statement that runs under none. */
    private static String runningStatementId() {
        final String running = StatementCancellation.runningQueryId();
        return running != null ? running : UUID.randomUUID().toString();
    }

    /**
     * Every table lock this transaction holds, whichever apply path recorded it: a lock both recorded is acquired
     * at the earlier touch and named by the statement the write set names, the latest that changed a row.
     */
    public Map<String, TableLock> getAllTableLocks() {
        if (writeSet.getTableLocks().isEmpty()) {
            return tableLocks;
        }
        final Map<String, TableLock> merged = new LinkedHashMap<>(tableLocks);
        for (final Map.Entry<String, TableLock> written : writeSet.getTableLocks().entrySet()) {
            final TableLock touched = merged.get(written.getKey());
            merged.put(written.getKey(), touched == null ? written.getValue() : new TableLock(
                Math.min(touched.getAcquiredOn(), written.getValue().getAcquiredOn()),
                written.getValue().getQueryId()));
        }
        return merged;
    }

    public void commit() {
        // Deferred-apply path: flush buffered writes to storage now (atomically), emit stream CDC at
        // apply time, then snapshot for time travel — so committed state (not per-DML state) is what
        // streams and time-travel observe. The immediate path leaves the write set empty (nothing here).
        // A DML statement that changed no row still writes the version a stream's backlog lists.
        if (!writeSet.isEmpty() || writeSet.notesStatements()) {
            try {
                writeSet.applyTo(storageEngine, streamManager);
                storageEngine.snapshotDirtyTables();
            } catch (final RuntimeException notApplied) {
                // The writes did not apply, so what the statements left for the transaction's end is taken back as a
                // rollback takes it: a load's files are not recorded as loaded, and a TRUNCATE keeps the history.
                undoOutcomesFrom(0);
                throw notApplied;
            }
        }
        // A stream used as a source in a committed DML advances its offset on commit to the transaction's start:
        // the changes committed since, the transaction's own among them, stay unconsumed (live-verified).
        for (final Map.Entry<Stream, StreamReadScope> entry : streamsToConsume.entrySet()) {
            entry.getKey().consumeTo(entry.getValue().getCut(), entry.getValue().getSequence());
        }
        streamsToConsume.clear();
        final List<TransactionOutcome> ended = new ArrayList<>(outcomes);
        outcomes.clear();
        for (final TransactionOutcome outcome : ended) {
            outcome.committed();
        }
        logs.clear();
        writeSet.clear();
        savepoints.clear();
        tableLocks.clear();
        insertLocks.clear();
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
        undoOutcomesFrom(0);
        logs.clear();
        writeSet.clear();
        savepoints.clear();
        tableLocks.clear();
        insertLocks.clear();
    }

    public long getStartTime() {
        return startTime;
    }

    /** @return the snapshot number the transaction drew when it began (see {@link #startSequence}) */
    public long getStartSequence() {
        return startSequence;
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
