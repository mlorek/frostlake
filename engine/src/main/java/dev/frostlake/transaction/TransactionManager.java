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
import dev.frostlake.metastore.QueryHistory;
import dev.frostlake.storage.StorageEngine;
import dev.frostlake.stream.StreamManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
        final long txnId = transactionIdGenerator.getAndIncrement();
        final Transaction txn = new Transaction(txnId, catalog, storageEngine, streamManager);
        // The session the statement runs in owns the transaction: its number is what SHOW TRANSACTIONS and
        // SHOW LOCKS print, and each session's differs.
        if (sessionContext != null) {
            txn.setSessionNumber(sessionContext.getSessionNumber());
        }
        activeTransactions.put(txnId, txn);
        currentTransaction.set(txn);
        return txn;
    }

    /** Begin (or promote the current auto-started) transaction to explicit — an explicit BEGIN suspends
     *  statement-end autocommit until COMMIT/ROLLBACK (Snowflake semantics). */
    public void beginExplicitTransaction() {
        beginExplicitTransaction(null);
    }

    /** As {@link #beginExplicitTransaction()}, naming the transaction: {@code BEGIN NAME <name>} puts that
     *  name in SHOW TRANSACTIONS' name cell in place of the UUID an unnamed one carries. */
    public void beginExplicitTransaction(final String name) {
        Transaction txn = currentTransaction.get();
        if (txn == null) {
            txn = beginTransaction();
        }
        txn.setExplicit(true);
        if (name != null) {
            txn.setName(name);
        }
    }

    /** True if an explicit (BEGIN-started) transaction is currently active. */
    public boolean isExplicitTransaction() {
        final Transaction txn = currentTransaction.get();
        return txn != null && txn.isExplicit();
    }

    // Session AUTOCOMMIT setting. Lives here (not on the engine facade) so statement-end autocommit can
    // run at EVERY statement chokepoint — top-level execute() and each statement inside a stored
    // procedure, which Snowflake commits individually unless an explicit BEGIN is open.
    private volatile boolean autoCommit = true;
    // Per-thread session override, mirroring Catalog's session scope: the concurrent front-end applies
    // each session's autocommit mode around its statement so parallel read-locked statements from
    // different sessions cannot see (or capture back) each other's mode. No entry → the shared field.
    private final ThreadLocal<Boolean> sessionAutoCommit = new ThreadLocal<>();

    /** Begin a per-thread autocommit scope; end it with {@link #clearSessionAutoCommit()}. */
    public void beginSessionAutoCommit(final boolean value) {
        sessionAutoCommit.set(Boolean.valueOf(value));
    }

    /** End this thread's autocommit scope (idempotent). Read the final value via {@link #isAutoCommit()} first. */
    public void clearSessionAutoCommit() {
        sessionAutoCommit.remove();
    }

    public void setAutoCommit(final boolean autoCommit) {
        if (sessionAutoCommit.get() != null) {
            sessionAutoCommit.set(Boolean.valueOf(autoCommit));
            return;
        }
        this.autoCommit = autoCommit;
    }

    public boolean isAutoCommit() {
        final Boolean scoped = sessionAutoCommit.get();
        return scoped != null ? scoped.booleanValue() : autoCommit;
    }

    /**
     * Statement-end autocommit: commit the current implicit transaction, if any. No-op when autocommit
     * is off or an explicit BEGIN suspended it (Snowflake semantics). Call after each completed
     * statement — the top-level engine entry point does, and so does the procedural executor for each
     * statement inside a BEGIN…END body (a stored procedure does NOT wrap its statements in one
     * transaction; leaving the implicit transaction open let a later same-row DELETE consolidate away a
     * buffered INSERT, so append-only streams missed changes that Snowflake captures).
     */
    public void autocommitStatementEnd() {
        if (atomicSectionDepth.get() > 0) {
            // A procedure running as part of an ENCLOSING statement (TABLE(proc()) in a FROM clause):
            // its statements execute inside that statement's transaction, so the per-statement
            // autocommit must not commit — the enclosing INSERT/MERGE still owns the write set.
            return;
        }
        // isAutoCommit(), not the global field: a JDBC session's autocommit=false lives in the
        // per-thread scope, and reading the field directly committed its statements regardless.
        if (isAutoCommit() && hasActiveTransaction() && !isExplicitTransaction()) {
            commit();
        }
    }

    // Depth of nested atomic sections (per thread): while > 0, autocommitStatementEnd() is a no-op.
    private final ThreadLocal<Integer> atomicSectionDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    /** Enter a section whose statements must not autocommit the enclosing statement's transaction. */
    public void beginAtomicSection() {
        atomicSectionDepth.set(atomicSectionDepth.get() + 1);
    }

    public void endAtomicSection() {
        final int depth = atomicSectionDepth.get();
        if (depth > 0) {
            atomicSectionDepth.set(depth - 1);
        }
    }

    /**
     * Discard any buffered (uncommitted) writes for a table being dropped or replaced, so a later COMMIT
     * doesn't try to flush them to storage that no longer exists — and so a drop-then-recreate of the same
     * name within one transaction doesn't resurrect the pre-drop rows. No-op when there is no active
     * transaction or the immediate-apply path left the write set empty. Called from the DROP/CREATE-OR-REPLACE
     * handlers, which remove the storage in the same step.
     */
    public void discardBufferedWritesFor(final String fullyQualifiedTableName) {
        final Transaction txn = currentTransaction.get();
        if (txn != null) {
            txn.getWriteSet().forgetTable(fullyQualifiedTableName);
        }
    }

    private dev.frostlake.security.SessionContext sessionContext;

    /** Wire the owning session so LAST_TRANSACTION can answer the completed id. */
    public void setSessionContext(final dev.frostlake.security.SessionContext sessionContext) {
        this.sessionContext = sessionContext;
    }

    /** Record the id of a transaction that is about to complete (commit or rollback). */
    private void publishCompletion(final Transaction txn) {
        if (txn != null && sessionContext != null) {
            sessionContext.setLastTransactionId(String.valueOf(txn.getPublicId()));
        }
    }

    public void commit() {
        final Transaction txn = currentTransaction.get();
        publishCompletion(txn);
        if (txn == null) {
            throw new RuntimeException("No active transaction");
        }

        if (txn.getState() != TransactionState.ACTIVE) {
            throw new RuntimeException("Transaction is not active");
        }

        // Captured before the txn is cleared below so the WAL append (after a successful apply) can use it.
        final List<String> walStatements = txn.getWalStatements();
        final List<Instant> walInstants = txn.getWalInstants();
        try {
            // Apply all changes
            txn.commit();
            txn.setState(TransactionState.COMMITTED);
            // The statements that ran inside it became visible now, not when each one finished.
            final LocalDateTime committedAt = LocalDateTime.now(ZoneOffset.UTC);
            for (final QueryHistory statement : txn.getStatements()) {
                statement.setVisibleFrom(committedAt);
                statement.markVisibleSequence();
            }
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
            walSink.appendTransaction(walStatements, walInstants);
        }
    }

    public void rollback() {
        final Transaction txn = currentTransaction.get();
        publishCompletion(txn);
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
            final Transaction txn = activeTransactions.get(transactionId);
            if (txn != null) {
                currentTransaction.set(txn);
            }
        }
    }

    public Collection<Transaction> getActiveTransactions() {
        return activeTransactions.values();
    }

    public Long getCurrentTransactionId() {
        final Transaction txn = currentTransaction.get();
        return txn != null ? txn.getId() : null;
    }

}
