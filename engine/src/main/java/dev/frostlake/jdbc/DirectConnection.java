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

package dev.frostlake.jdbc;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.executor.SessionZone;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.security.AbortableSession;
import dev.frostlake.security.SessionContext;
import dev.frostlake.security.SessionDirectory;
import dev.frostlake.security.SessionSettings;
import dev.frostlake.transaction.EngineLockRelease;
import dev.frostlake.transaction.TableLockGate;
import dev.frostlake.transaction.TransactionManager;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Struct;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Direct JDBC connection that wraps a DatabaseEngine instance without HTTP.
 * Used for testing and embedded usage.
 */
public class DirectConnection extends AbstractFrostlakeConnection
        implements AbortableSession, ResultColumnCase, SessionZoneSource {

    private final DatabaseEngine engine;
    private boolean closed = false;
    // This connection's own session context over the SHARED per-name engine, mirroring what a real
    // Snowflake connection carries. Every statement runs inside a per-thread scope built from these,
    // so a USE on one connection (possibly on another thread — the test harness runs bulk checks on
    // pool threads) can never leak into another connection's name resolution mid-statement.
    private String sessionDatabase;
    private String sessionSchema;
    private String sessionWarehouse;
    private boolean sessionAutoCommit;

    /**
     * The id of THIS connection's open transaction, or null when it has none.
     *
     * <p>The engine keeps its current transaction in a thread-local, so two connections driven from one
     * thread would otherwise share it and each would see the other's uncommitted rows. Live keeps them
     * apart, so the id is held here and rebound before every statement — the same mechanism the HTTP
     * front-end uses to isolate its sessions.
     */
    private Long sessionTransactionId;

    /**
     * Every transaction a direct connection has opened, across all of them.
     *
     * <p>A connection with no transaction of its own must clear a binding left by ANOTHER connection,
     * or it reads that connection's uncommitted rows — but it must NOT clear a binding left by plain
     * engine use on the same thread, which the statement machinery carries between statements. Only a
     * transaction in this set is known to belong to a connection, so only that one is cleared.
     */
    private static final Set<Long> CONNECTION_OWNED_TRANSACTIONS =
        Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    /** The session's MULTI_STATEMENT_COUNT (1 = single statement only, 0 = any). */
    private int multiStatementCount = 1;

    /**
     * This connection's ALTER SESSION parameters and SET variables, bound for each of its statements; or null for a
     * connection that shares the engine's own. Live keeps them per connection: a TIMEZONE, a QUERY_TAG or a variable
     * one connection sets is invisible to another connection to the same name.
     */
    private final SessionSettings sessionSettings;

    /** What a statement gets once SYSTEM$ABORT_SESSION ended this connection's session, as the account words it. */
    static final String SESSION_GONE = SessionDirectory.SESSION_GONE;

    /** Set once another statement aborted this connection's session; every later statement is refused. */
    private volatile boolean aborted;
    // Whether a column label is matched ignoring case: once the session set CLIENT_RESULT_COLUMN_CASE_INSENSITIVE to
    // TRUE, for the rest of the connection (see ResultColumnCase).
    private volatile boolean labelsIgnoreCase;
    /** The TIMEZONE the session named when its last statement ran, its own or inherited; null before any ran. */
    private volatile String sessionTimezone;
    /** Whether the aborted session held an open transaction, for the refusal's report. */
    private volatile boolean abortedInTransaction;

    public DirectConnection(final DatabaseEngine engine) {
        this(engine, true);
    }

    /**
     * A connection to an engine.
     *
     * @param engine the engine
     * @param ownSession whether the connection keeps session parameters and variables of its own, as every connection
     *                   a driver URL opens does; false shares the engine's, for a caller that wraps an engine it
     *                   also reads directly
     */
    public DirectConnection(final DatabaseEngine engine, final boolean ownSession) {
        this.engine = engine;
        this.sessionDatabase = engine.getCurrentDatabase();
        this.sessionSchema = engine.getCurrentSchema();
        this.sessionWarehouse = ownSession ? engine.getCatalog().sessionStartWarehouse()
            : engine.getCatalog().getCurrentWarehouse();
        this.sessionAutoCommit = engine.isAutoCommit();
        this.sessionSettings = ownSession ? new SessionSettings() : null;
        if (sessionSettings != null) {
            engine.getSessionDirectory().register(sessionSettings.getSessionNumber(), this);
        }
    }

    /** SYSTEM$ABORT_SESSION named this connection's session: only marked here, from the aborting statement. */
    @Override
    public void abort() {
        abortedInTransaction = sessionTransactionId != null;
        aborted = true;
    }

    /** Roll back what the aborted session left open, and drop what it created TEMPORARY, once the aborting
     *  statement is over. */
    @Override
    public void endAborted() {
        endOwnTransaction();
        dropOwnTemporaryObjects();
    }

    /**
     * Refuse a statement on a session that was aborted, as the account does: the session is gone for good, so
     * every later statement gets the same answer.
     *
     * @throws SQLException when the session was aborted
     */
    void requireSession() throws SQLException {
        if (aborted) {
            endOwnTransaction();
            throw new FrostlakeSessionLostException(SESSION_GONE, abortedInTransaction);
        }
    }

    int getMultiStatementCount() {
        return multiStatementCount;
    }

    void setMultiStatementCount(final int count) {
        this.multiStatementCount = count;
    }

    /**
     * Execute one statement under THIS connection's session context: the context is bound to the
     * current thread for the statement's duration (so concurrent connections stay isolated on the
     * shared engine) and the post-statement context — a USE, an ALTER of autocommit, an opened or
     * closed transaction — is captured back into the connection.
     */
    /** How a statement waiting for a table lock gives up the engine's monitor: each pause waits on it. */
    private EngineLockRelease monitorRelease() {
        return new EngineLockRelease() {
            @Override
            public void release() {
                // The monitor is given up by each pause.
            }

            @Override
            public void pause(final long millis) throws InterruptedException {
                engine.wait(millis);
            }

            @Override
            public void reacquire() {
                // Each pause takes the monitor back as it returns.
            }
        };
    }

    ExecutionResult executeScoped(final String sql) {
        if (aborted) {
            throw new IllegalStateException(SESSION_GONE);
        }
        // One statement at a time on the shared engine: the embedded engine's procedural state
        // (variable scopes, handler stacks) is not safe under concurrent execution, and direct
        // connections can be driven from many threads (the test harness's pooled bulk checks).
        // The HTTP front-end serializes through its own lock; direct connections serialize here.
        synchronized (engine) {
            final Catalog catalog = engine.getCatalog();
            final TransactionManager transactions = engine.getTransactionManager();
            catalog.beginSessionScope(sessionDatabase, sessionSchema, sessionWarehouse);
            transactions.beginSessionAutoCommit(sessionAutoCommit);
            final SessionContext session = engine.getSecurityManager().getSessionContext();
            if (sessionSettings != null) {
                session.bindSettings(sessionSettings);
            }
            // Restore this connection's transaction, or clear ANOTHER connection's: skipping that would
            // let this connection inherit whatever the previous one on this thread left open, and read
            // its uncommitted writes.
            bindOwnTransaction(transactions);
            // A statement that waits for another connection's table lock waits on the engine's monitor, which it
            // gives up meanwhile, so the holder's own statements can end its transaction (see TableLockGate). The
            // session's context is bound to this thread, so nothing of it moves.
            final boolean mayWait = sessionSettings != null;
            final EngineLockRelease displaced = mayWait ? TableLockGate.register(monitorRelease()) : null;
            try {
                return engine.execute(sql);
            } finally {
                if (mayWait) {
                    TableLockGate.restore(displaced);
                }
                // A statement that waits for a lock this one may have let go looks again at once.
                engine.notifyAll();
                // Captured when the request fails too: a request of several statements that fails part-way
                // keeps what its earlier statements did — a transaction a BEGIN opened stays open in the
                // session (live-verified) — so the connection must take it as its own. Left on the thread
                // uncaptured, it was adopted by whichever connection ran next there, or orphaned.
                sessionDatabase = catalog.getCurrentDatabase();
                sessionSchema = catalog.heldSchemaName();
                sessionWarehouse = catalog.getCurrentWarehouse();
                sessionAutoCommit = transactions.isAutoCommit();
                sessionTransactionId = transactions.getCurrentTransactionId();
                if (sessionTransactionId != null) {
                    CONNECTION_OWNED_TRANSACTIONS.add(sessionTransactionId);
                }
                if (session.resultColumnsCaseInsensitive()) {
                    labelsIgnoreCase = true;
                }
                final Object timezone = session.getSessionParameter("TIMEZONE");
                sessionTimezone = timezone == null ? null : timezone.toString();
                catalog.clearSessionScope();
                transactions.clearSessionAutoCommit();
                if (sessionSettings != null) {
                    session.unbindSettings();
                }
                // A session this statement aborted — its own included — ends now that the statement is over.
                engine.getSessionDirectory().endAborted();
            }
        }
    }

    /**
     * Bind this connection's transaction for the statement about to run.
     *
     * @param transactions the engine's transaction manager
     */
    private void bindOwnTransaction(final TransactionManager transactions) {
        if (sessionTransactionId != null) {
            transactions.setCurrentTransaction(sessionTransactionId);
            return;
        }
        final Long bound = transactions.getCurrentTransactionId();
        if (bound != null && CONNECTION_OWNED_TRANSACTIONS.contains(bound)) {
            transactions.setCurrentTransaction(null);
        }
    }

    @Override
    public Statement createStatement() throws SQLException {
        checkClosed();
        return new DirectStatement(this, engine);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql) throws SQLException {
        checkClosed();
        return new DirectPreparedStatement(this, engine, sql);
    }

    @Override
    public void close() throws SQLException {
        if (closed) {
            return;
        }
        closed = true;
        if (sessionSettings != null) {
            engine.getSessionDirectory().unregister(sessionSettings.getSessionNumber());
        }
        endOwnTransaction();
        dropOwnTemporaryObjects();
    }

    /**
     * Drop what this connection's session created TEMPORARY, as the session ends: a temporary object lives as long
     * as its session, and on an account a connection is one. A connection that shares the engine's own session
     * leaves its objects to the engine.
     */
    private void dropOwnTemporaryObjects() {
        if (sessionSettings == null) {
            return;
        }
        synchronized (engine) {
            try {
                engine.dropTemporaryObjectsOf(sessionSettings.getSessionNumber());
            } catch (final RuntimeException ignored) {
                // The session is gone either way; what could not be dropped goes when the engine shuts down.
            }
        }
    }

    /**
     * Roll back the transaction this connection leaves open, as the HTTP front-end does when a session is
     * released: the session that owned it is gone, so nothing could ever COMMIT it, and it would otherwise
     * stay in SHOW TRANSACTIONS for the engine's lifetime. The thread's own binding is put back afterwards.
     */
    private void endOwnTransaction() {
        final Long open = sessionTransactionId;
        if (open == null) {
            return;
        }
        synchronized (engine) {
            final TransactionManager transactions = engine.getTransactionManager();
            final Long bound = transactions.getCurrentTransactionId();
            transactions.setCurrentTransaction(open);
            try {
                if (open.equals(transactions.getCurrentTransactionId())) {
                    transactions.rollback();
                }
            } catch (final RuntimeException ignored) {
                // The connection goes regardless; its transaction's writes were never committed.
            } finally {
                transactions.setCurrentTransaction(open.equals(bound) ? null : bound);
            }
        }
        CONNECTION_OWNED_TRANSACTIONS.remove(open);
        sessionTransactionId = null;
    }

    @Override
    public boolean isClosed() throws SQLException {
        return closed;
    }

    @Override
    public boolean labelsIgnoreCase() {
        return labelsIgnoreCase;
    }

    @Override
    public ZoneId sessionZone() {
        final ZoneId zone = sessionTimezone == null ? null : SessionZone.named(sessionTimezone);
        return zone != null ? zone : ZoneOffset.UTC;
    }

    /** Whether the connection wraps a type: the embedded engine behind it too. */
    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return iface == DatabaseEngine.class || super.isWrapperFor(iface);
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        // The embedded engine behind this connection — how in-process harnesses reach engine-level
        // APIs (checkpointStateTo/restoreStateFrom) that have no SQL surface.
        if (iface == DatabaseEngine.class) {
            return iface.cast(engine);
        }
        return super.unwrap(iface);
    }

    @Override
    public boolean isValid(final int timeout) throws SQLException {
        requireValidTimeout(timeout);
        return !closed && !aborted;
    }

    @Override
    public void setAutoCommit(final boolean autoCommit) throws SQLException {
        checkClosed();
        // The connection's session context supplies the mode to every statement via executeScoped —
        // writing the engine's global flag here would leak the mode into other connections.
        this.sessionAutoCommit = autoCommit;
    }

    @Override
    public boolean getAutoCommit() throws SQLException {
        checkClosed();
        // The session's EFFECTIVE mode: an ALTER SESSION SET AUTOCOMMIT executed over this
        // connection moves it too, not just setAutoCommit.
        return sessionAutoCommit;
    }

    @Override
    public void commit() throws SQLException {
        checkClosed();
        requireSession();
        endTransaction("COMMIT");
    }

    @Override
    public void rollback() throws SQLException {
        checkClosed();
        requireSession();
        endTransaction("ROLLBACK");
    }

    /** A COMMIT can fail — of a transaction that was aborted, for one — and fails as a statement's does. */
    private void endTransaction(final String statement) throws SQLException {
        try {
            executeScoped(statement);
        } catch (final RuntimeException failed) {
            throw new SQLException(failed.getMessage(), failed);
        }
    }

    // Unimplemented methods required by Connection interface

    @Override
    public CallableStatement prepareCall(final String sql) throws SQLException {
        checkClosed();
        return new DirectCallableStatement(this, engine, sql);
    }

    @Override
    public java.sql.DatabaseMetaData getMetaData() throws SQLException {
        checkClosed();
        return new DirectDatabaseMetaData(this);
    }

    @Override
    public void setCatalog(final String catalog) throws SQLException {
        checkClosed();
        requireSession();
        executeScoped("USE DATABASE " + catalog);
    }

    @Override
    public String getCatalog() throws SQLException {
        checkClosed();
        return sessionDatabase;
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int autoGeneratedKeys) throws SQLException {
        checkClosed();
        return new DirectPreparedStatement(this, engine, sql, autoGeneratedKeys);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final int[] columnIndexes) throws SQLException {
        checkClosed();
        // Treat as RETURN_GENERATED_KEYS if column indexes specified
        return new DirectPreparedStatement(this, engine, sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public PreparedStatement prepareStatement(final String sql, final String[] columnNames) throws SQLException {
        checkClosed();
        // Treat as RETURN_GENERATED_KEYS if column names specified
        return new DirectPreparedStatement(this, engine, sql, Statement.RETURN_GENERATED_KEYS);
    }

    @Override
    public Struct createStruct(final String typeName, final Object[] attributes) throws SQLException {
        checkClosed();
        // For Struct, we need attribute names, but JDBC createStruct doesn't provide them
        // Generate default attribute names: ATTR1, ATTR2, etc.
        final String[] attributeNames = new String[attributes.length];
        for (int i = 0; i < attributes.length; i++) {
            attributeNames[i] = "ATTR" + (i + 1);
        }
        return new DirectStruct(typeName, attributeNames, attributes);
    }

    @Override
    public void setSchema(final String schema) throws SQLException {
        checkClosed();
        requireSession();
        executeScoped("USE SCHEMA " + schema);
    }

    @Override
    public String getSchema() throws SQLException {
        checkClosed();
        return sessionSchema;
    }

    /**
     * The user this connection's statements run as, as CURRENT_USER() names it.
     *
     * @return the user's name
     */
    String userName() {
        return engine.getSecurityManager().getSessionContext().getDisplayUser();
    }

    @Override
    public void abort(final Executor executor) throws SQLException {
        close();
    }

}
