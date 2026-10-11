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
    // Transactions SYSTEM$ABORT_TRANSACTION ended while a session still held them, by id, and the one this
    // thread's session holds: every statement of that session is refused until it issues a COMMIT or a ROLLBACK.
    private final Map<Long, Transaction> abortedTransactions = new ConcurrentHashMap<>();
    private final ThreadLocal<Transaction> abortedCurrent = new ThreadLocal<>();
    private final Catalog catalog;
    private final StorageEngine storageEngine;
    private StreamManager streamManager;
    private WalSink walSink;   // durability hook: receives a transaction's statements when it commits

    /** How a scope that did not begin the transaction is refused a COMMIT, a ROLLBACK or a statement that commits. */
    public static final String OTHER_SCOPE =
        "Modifying a transaction that has started at a different scope is not allowed.";

    /** How a block or a procedure that ends with a transaction of its own still open is refused. */
    public static final String SCOPE_LEFT_OPEN = "Stored procedure execution error: "
        + "Scoped transaction started in stored procedure is incomplete and it was rolled back.";

    // How deep this thread's statement is in transaction scopes: 0 for a statement of the session's own, one more
    // inside each block statement, procedure body and text that is a block. A scope is over before its statement
    // is, so the depth is the thread's and not the session's.
    private final ThreadLocal<Integer> scopeDepth = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            return Integer.valueOf(0);
        }
    };

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
        final int depth = scopeDepth.get().intValue();
        if (txn != null && txn.isExplicit() && txn.getScope() < depth) {
            // An enclosing scope's transaction is open: this scope's BEGIN starts one of its own beside it, which
            // commits and rolls back by itself and sees nothing the other has not committed. The enclosing one
            // waits until this one ends (live-verified).
            final Transaction outer = txn;
            currentTransaction.remove();
            txn = beginTransaction();
            txn.setOuter(outer);
        } else if (txn == null) {
            txn = beginTransaction();
        }
        if (!txn.isExplicit()) {
            txn.setScope(depth);
        }
        txn.setExplicit(true);
        if (name != null) {
            txn.setName(name);
        }
    }

    /**
     * Enters a transaction scope: a block statement, a procedure's body or a text that is a block starts running.
     * A transaction is the scope's that began it — a scope inside may add to it and may not end it — and a scope
     * that begins one while an enclosing scope's is open gets a transaction of its own (live-verified).
     */
    public void enterScope() {
        scopeDepth.set(Integer.valueOf(scopeDepth.get().intValue() + 1));
    }

    /**
     * How many scopes are running on this thread, the innermost included: the level of the body that is running,
     * zero for a statement the session sends on its own.
     *
     * @return the depth {@link #enterScope} has reached
     */
    public int scopeDepth() {
        return scopeDepth.get().intValue();
    }

    /**
     * Leaves the scope {@link #enterScope} entered. A transaction the scope began and left open is rolled back
     * here, and the enclosing scope's transaction it was begun beside is the session's again.
     *
     * @return whether the scope left a transaction of its own open, which its statement reports as
     *         {@link #SCOPE_LEFT_OPEN}
     */
    public boolean exitScope() {
        final int depth = scopeDepth.get().intValue();
        final Transaction txn = currentTransaction.get();
        boolean leftOpen = false;
        if (depth > 0 && txn != null && txn.isExplicit() && txn.getScope() >= depth) {
            leftOpen = true;
            try {
                rollback();
            } catch (final RuntimeException rollbackFailure) {
                logger.warn("Could not roll back a transaction a scope left open: {}", rollbackFailure.getMessage());
            }
        }
        scopeDepth.set(Integer.valueOf(Math.max(0, depth - 1)));
        return leftOpen;
    }

    /**
     * Refuses a statement that would end the open transaction from a scope that did not begin it: a COMMIT, a
     * ROLLBACK or a statement that commits, in a block or a procedure called while its caller's transaction is
     * open (live-verified).
     */
    public void requireOwnScope() {
        final Transaction txn = currentTransaction.get();
        if (txn != null && txn.isExplicit() && txn.getScope() != scopeDepth.get().intValue()) {
            throw new RuntimeException(OTHER_SCOPE);
        }
    }

    /** The transaction that ended was begun beside an enclosing scope's: that one is the thread's again. */
    private void resumeOuter(final Transaction ended) {
        final Transaction outer = ended.getOuter();
        if (outer != null) {
            ended.setOuter(null);
            bind(outer);
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

    /**
     * Record a transaction for a statement that ran as one of its own without this manager seeing it begin: a
     * CREATE TABLE … AS SELECT, a table CLONE, a CREATE STREAM and a COPY into a table each do, and
     * LAST_TRANSACTION answers a new id after them. Inside an open transaction nothing is recorded: that
     * transaction's own end is.
     */
    public void recordStatementTransaction() {
        if (sessionContext == null || currentTransaction.get() != null) {
            return;
        }
        final Transaction txn = new Transaction(transactionIdGenerator.getAndIncrement(), catalog, storageEngine,
            streamManager);
        sessionContext.setLastTransactionId(String.valueOf(txn.getPublicId()));
    }

    /** Record the id of a transaction that is about to complete (commit or rollback). */
    private void publishCompletion(final Transaction txn) {
        if (txn != null && sessionContext != null) {
            sessionContext.setLastTransactionId(String.valueOf(txn.getPublicId()));
        }
    }

    /**
     * Ends the open transaction of a public id on behalf of whoever names it — the caller's own or another
     * session's (live-verified): its writes are rolled back and its locks released at once. The session that held
     * it is left holding an aborted transaction, and each of its statements is refused until it issues a COMMIT,
     * which fails, or a ROLLBACK. A transaction a running block began and ends this way is simply over: the block
     * goes on outside any transaction.
     *
     * @param publicId    the id CURRENT_TRANSACTION() answers inside the transaction
     * @param insideBlock whether a Snowflake Scripting block or a procedure is running the abort
     * @return whether a transaction of that id was open
     */
    public boolean abort(final long publicId, final boolean insideBlock) {
        Transaction named = null;
        for (final Transaction open : activeTransactions.values()) {
            if (open.getPublicId() == publicId) {
                named = open;
                break;
            }
        }
        if (named == null) {
            return false;
        }
        final boolean own = named == currentTransaction.get();
        try {
            named.rollback();
            named.setState(TransactionState.ROLLED_BACK);
        } finally {
            activeTransactions.remove(named.getId());
            if (own) {
                currentTransaction.remove();
            }
        }
        if (own && insideBlock && named.isBegunInBlock()) {
            publishCompletion(named);
            resumeOuter(named);
            return true;
        }
        abortedTransactions.put(named.getId(), named);
        if (own) {
            abortedCurrent.set(named);
        }
        return true;
    }

    /** The transaction this thread's session holds although it was aborted, or null when it holds none. */
    public Transaction getAbortedTransaction() {
        return abortedCurrent.get();
    }

    /**
     * Lets go of the aborted transaction this thread's session holds, which a COMMIT or a ROLLBACK does. The
     * transaction is the session's last from then on.
     *
     * @return the transaction, or null when the session held none
     */
    public Transaction acknowledgeAborted() {
        final Transaction aborted = abortedCurrent.get();
        if (aborted != null) {
            abortedCurrent.remove();
            abortedTransactions.remove(aborted.getId());
            publishCompletion(aborted);
        }
        return aborted;
    }

    public void commit() {
        final Transaction txn = currentTransaction.get();
        if (txn == null) {
            final Transaction aborted = acknowledgeAborted();
            if (aborted != null) {
                throw new RuntimeException(TransactionAbortedMessages.commitFailed(aborted));
            }
        }
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
            resumeOuter(txn);
        }

        // Durability: the commit has applied successfully — now append it to the WAL and fsync (a failure
        // here surfaces to the caller). Done after apply so the log only ever holds committed work.
        if (walSink != null && !walStatements.isEmpty()) {
            walSink.appendTransaction(walStatements, walInstants);
        }
    }

    public void rollback() {
        final Transaction txn = currentTransaction.get();
        if (txn == null && acknowledgeAborted() != null) {
            return;
        }
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
            resumeOuter(txn);
        }
    }

    /**
     * Roll back the current transaction without recording it as the session's last: the transaction was begun
     * for a statement that then never ran.
     */
    public void rollbackUnrecorded() {
        final Transaction txn = currentTransaction.get();
        if (txn == null) {
            throw new RuntimeException("No active transaction");
        }
        try {
            txn.rollback();
            txn.setState(TransactionState.ROLLED_BACK);
        } finally {
            activeTransactions.remove(txn.getId());
            currentTransaction.remove();
            resumeOuter(txn);
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

    /**
     * Sets this thread's transaction aside for work that runs as a session of its own: a task's run, an alert's
     * evaluation, a pipe's load. Such work never joins the caller's transaction, so the caller's ROLLBACK cannot
     * undo it. It starts with no transaction, with autocommit on whatever the caller's session chose, and outside
     * any atomic section and transaction scope, so each of its statements commits at its end. Hand the answer back to
     * {@link #resumeTransaction}.
     *
     * @return what was set aside
     */
    public SetAsideTransaction setAsideTransaction() {
        final Transaction held = currentTransaction.get() != null ? currentTransaction.get() : abortedCurrent.get();
        final SetAsideTransaction aside = new SetAsideTransaction(held, sessionAutoCommit.get(),
            atomicSectionDepth.get(), scopeDepth.get().intValue());
        currentTransaction.remove();
        abortedCurrent.remove();
        sessionAutoCommit.set(Boolean.TRUE);
        atomicSectionDepth.set(0);
        scopeDepth.set(Integer.valueOf(0));
        return aside;
    }

    /**
     * Puts back what {@link #setAsideTransaction} set aside. A transaction the work left open, because a statement
     * failed before its end or the work opened one and never committed it, is rolled back first, as the end of a
     * session rolls back what it left open.
     *
     * @param aside what was set aside
     */
    public void resumeTransaction(final SetAsideTransaction aside) {
        if (currentTransaction.get() != null) {
            try {
                rollback();
            } catch (final RuntimeException leftOpen) {
                logger.warn("Could not roll back a transaction left open by a task, alert or pipe: {}",
                    leftOpen.getMessage());
                currentTransaction.remove();
            }
        }
        // An aborted transaction the work left unacknowledged goes with the work.
        acknowledgeAbortedUnrecorded();
        if (aside.transaction() != null) {
            bind(aside.transaction());
        }
        if (aside.sessionAutoCommit() != null) {
            sessionAutoCommit.set(aside.sessionAutoCommit());
        } else {
            sessionAutoCommit.remove();
        }
        atomicSectionDepth.set(aside.atomicSectionDepth());
        scopeDepth.set(Integer.valueOf(aside.scopeDepth()));
    }

    public boolean hasActiveTransaction() {
        return currentTransaction.get() != null;
    }

    /**
     * Binds a session's transaction to this thread for the statement about to run, or unbinds whatever the thread
     * held when the session has none. A transaction that was aborted since the session last ran is bound as that:
     * the session's statements are refused until it acknowledges it.
     *
     * @param transactionId the session's transaction, as {@link #getCurrentTransactionId} answered it, or null
     */
    public void setCurrentTransaction(final Long transactionId) {
        if (transactionId == null) {
            currentTransaction.remove();
            abortedCurrent.remove();
            return;
        }
        final Transaction txn = activeTransactions.get(transactionId);
        if (txn != null) {
            currentTransaction.set(txn);
            abortedCurrent.remove();
            return;
        }
        final Transaction aborted = abortedTransactions.get(transactionId);
        if (aborted != null) {
            currentTransaction.remove();
            abortedCurrent.set(aborted);
        }
    }

    /** Binds a transaction this thread held before as what it is now: open, aborted, or gone. */
    private void bind(final Transaction txn) {
        if (activeTransactions.get(txn.getId()) == txn) {
            currentTransaction.set(txn);
        } else if (abortedTransactions.get(txn.getId()) == txn) {
            abortedCurrent.set(txn);
        }
    }

    private void acknowledgeAbortedUnrecorded() {
        final Transaction aborted = abortedCurrent.get();
        if (aborted != null) {
            abortedCurrent.remove();
            abortedTransactions.remove(aborted.getId());
        }
    }

    public Collection<Transaction> getActiveTransactions() {
        return activeTransactions.values();
    }

    /**
     * The id of the transaction this thread's session holds, an aborted one it has not acknowledged included: a
     * front end keeps the id with the session and hands it back to {@link #setCurrentTransaction}.
     */
    public Long getCurrentTransactionId() {
        final Transaction txn = currentTransaction.get() != null ? currentTransaction.get() : abortedCurrent.get();
        return txn != null ? txn.getId() : null;
    }

}
