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

package dev.frostlake;

import dev.frostlake.config.EngineConfig;
import dev.frostlake.executor.ReadOnlyRequest;
import dev.frostlake.functions.scalar.file.NamedStage;
import dev.frostlake.http.ExpiredSessionListener;
import dev.frostlake.http.SessionContext;
import dev.frostlake.http.SessionManager;
import dev.frostlake.metastore.Catalog;
import dev.frostlake.storage.ResultSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe wrapper around DatabaseEngine for concurrent multi-user access
 * Manages sessions and ensures proper isolation between concurrent requests
 */
public class ConcurrentDatabaseEngine {
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentDatabaseEngine.class);

    private final DatabaseEngine engine;
    private final SessionManager sessionManager;
    private final ReadWriteLock engineLock;

    // Track per-session state
    private final ConcurrentHashMap<String, SessionState> sessionStates;

    // Each session's entry in the engine's directory of abortable sessions, by session id. The directory holds an
    // entry weakly, so this map is what keeps it for as long as the session lives.
    private final ConcurrentHashMap<String, AbortableHttpSession> abortable =
        new ConcurrentHashMap<String, AbortableHttpSession>();

    public ConcurrentDatabaseEngine() {
        this(new DatabaseEngine());
    }

    /** A concurrent wrapper honoring the given configuration (database.default / schema.default). */
    public ConcurrentDatabaseEngine(final EngineConfig config) {
        this(new DatabaseEngine(config));
    }

    private ConcurrentDatabaseEngine(final DatabaseEngine engine) {
        this.engine = engine;
        // New sessions start where the engine's configuration points, exactly like the engine's
        // own bootstrap session, and idle out after http.session.idleTimeoutMinutes (Snowflake's four hours
        // unless set; 0 keeps them until they are released).
        this.sessionManager = new SessionManager(
            engine.getConfig().getDefaultDatabase().toUpperCase(Locale.ROOT),
            engine.getConfig().getDefaultSchema().toUpperCase(Locale.ROOT),
            engine.getConfig().getSessionIdleTimeoutMinutes() * 60L * 1000L);
        // Non-fair: higher read throughput for this read-heavy engine. The JDK's non-fair RRWL still
        // blocks barging readers when a writer is queued, so writers are not badly starved.
        this.engineLock = new ReentrantReadWriteLock();
        this.sessionStates = new ConcurrentHashMap<>();
        // An expired session is ended the way a released one is — its transaction rolled back and the
        // engine's state for it dropped — rather than merely forgotten by the manager.
        this.sessionManager.setExpiredSessionListener(new ExpiredSessionListener() {
            @Override
            public void expired(final SessionContext session) {
                endSession(session);
            }
        });
        logger.info("Initialized concurrent Frostlake engine");
    }

    /**
     * Execute SQL in a session context
     */
    public ExecutionResult execute(final String sql, final SessionContext session) {
        return execute(sql, session, null);
    }

    /**
     * Execute SQL in a session context, the session first following the autocommit mode the client
     * declares for this request (see {@link SessionContext#followClientAutoCommit(Boolean)}).
     *
     * @param sql the statement text
     * @param session the session to run in
     * @param clientAutoCommit the request's autocommit mode, or null when it declares none
     * @return the statement's result
     */
    public ExecutionResult execute(final String sql, final SessionContext session,
                                   final Boolean clientAutoCommit) {
        if (session == null) {
            throw new IllegalArgumentException("Session context is required");
        }
        registerForAbort(session);
        try {
            return executeInSession(sql, session, clientAutoCommit);
        } finally {
            // A session this request aborted — its own included — ends now that the request holds no lock.
            engine.getSessionDirectory().endAborted();
        }
    }

    /**
     * Make a session abortable by SYSTEM$ABORT_SESSION under the number CURRENT_SESSION() answers in it. An
     * aborted session ends as a released one does, so its next request names no live session.
     */
    private void registerForAbort(final SessionContext session) {
        if (abortable.containsKey(session.getSessionId())) {
            return;
        }
        final AbortableHttpSession entry = new AbortableHttpSession(this, session);
        if (abortable.putIfAbsent(session.getSessionId(), entry) == null) {
            engine.getSessionDirectory().register(entry.sessionNumber(), entry);
        }
    }

    private ExecutionResult executeInSession(final String sql, final SessionContext session,
                                             final Boolean clientAutoCommit) {
        session.touch();

        // Synchronize on session to prevent concurrent modifications to same session
        // putIfAbsent rather than get/put: two requests for one session must synchronize on the
        // SAME state object, so a lost race here would defeat the lock below.
        final String sessionId = session.getSessionId();
        SessionState state = sessionStates.get(sessionId);
        if (state == null) {
            state = new SessionState();
            final SessionState raced = sessionStates.putIfAbsent(sessionId, state);
            if (raced != null) {
                state = raced;
            }
        }

        synchronized (state) {
            // Under the session's lock, so the mode applied below is the one this request declared.
            session.followClientAutoCommit(clientAutoCommit);
            try {
                // Read lock for read-only scripts, write lock for anything that mutates — decided
                // ONCE per request from the parse tree (the unlock below must mirror the decision).
                final boolean readOnly = isReadOnlyQuery(sql);
                if (readOnly) {
                    engineLock.readLock().lock();
                } else {
                    engineLock.writeLock().lock();
                }

                try {
                    // Apply session context before execution (inside lock for thread safety)
                    applySessionContext(session);

                    final long startTime = System.currentTimeMillis();
                    final ExecutionResult result;
                    try {
                        result = engine.execute(sql);
                    } catch (final RuntimeException failed) {
                        // A request that fails part-way keeps what its earlier statements did, so the
                        // session must take it back: a transaction a BEGIN opened stays open in the session
                        // (live-verified — CURRENT_TRANSACTION() is still set, the session reads its own
                        // rows, and COMMIT or ROLLBACK ends it), and a USE stays in force. Left uncaptured,
                        // the transaction stayed with this pooled thread, the session's next request cleared
                        // it, and SHOW TRANSACTIONS listed it for the server's lifetime under no session.
                        captureSessionContext(session);
                        throw failed;
                    }
                    final long duration = System.currentTimeMillis() - startTime;

                    logger.debug("Executed SQL in session {} ({}ms): {}",
                        session.getSessionId(), duration, sql.substring(0, Math.min(50, sql.length())));

                    // Update session context after execution (reads this thread's scope, so it must run
                    // before the finally block clears it)
                    captureSessionContext(session);

                    return result;

                } finally {
                    // Always drop this thread's per-session scopes (idempotent) — a failed statement must
                    // not leak its session's context into whatever runs on this pooled thread next.
                    engine.getCatalog().clearSessionScope();
                    engine.getTransactionManager().clearSessionAutoCommit();
                    engine.getQueryResultCache().clearSessionScope();
                    engine.getSecurityManager().getSessionContext().unbindSettings();
                    // The transaction now belongs to the session (captured above): unbind it from this
                    // pooled thread, which serves other sessions next.
                    engine.getTransactionManager().setCurrentTransaction(null);
                    if (readOnly) {
                        engineLock.readLock().unlock();
                    } else {
                        engineLock.writeLock().unlock();
                    }
                }
            } catch (final Exception e) {
                logger.error("Error executing SQL in session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
                throw e;
            }
        }
    }

    /**
     * Execute query and return single result set
     */
    public ResultSet executeQuery(final String sql, final SessionContext session) {
        final ExecutionResult result = execute(sql, session);
        if (!result.isSuccess()) {
            throw new RuntimeException("Query execution failed: " + result.getErrorMessage());
        }
        if (result.getResultSets().isEmpty()) {
            return new ResultSet(List.of());
        }
        return result.getResultSets().get(0);
    }

    /**
     * Whether EVERY statement in {@code sql} may run under the SHARED read lock — decided from the
     * PARSE TREE, not a text prefix: 'SELECT 1; DROP TABLE t' is a write, and so is
     * 'SELECT seq1.NEXTVAL' (the sequence advances) and a query that reaches a write through TO_QUERY or a
     * system function that acts (see {@link ReadOnlyRequest}). Anything unparseable classifies as a WRITE,
     * so the exclusive lock is the failure mode.
     */
    private boolean isReadOnlyQuery(final String sql) {
        return ReadOnlyRequest.reads(sql);
    }

    /**
     * Apply session context to engine
     */
    private void applySessionContext(final SessionContext session) {
        try {
            // Bind this session's context to the CURRENT THREAD instead of mutating the shared engine
            // state: statements from different sessions run concurrently under the read lock, and the
            // old useDatabase/setAutoCommit calls clobbered one shared field — a USE in one session
            // leaked into another, and captureSessionContext then wrote the WRONG database back into
            // the session (which is how a session ended up pointed at another test's dropped clone).
            // The scopes are cleared in execute()'s finally.
            // The current warehouse is the session's own too: a USE WAREHOUSE in one session moved every
            // session's CURRENT_WAREHOUSE() while the engine kept a single one. A session that has run no
            // statement yet starts from the engine's.
            final Catalog catalog = engine.getCatalog();
            catalog.beginSessionScope(session.getCurrentDatabase(), session.getCurrentSchema(),
                session.isWarehouseSettled() ? session.getCurrentWarehouse() : catalog.getCurrentWarehouse());
            engine.getTransactionManager().beginSessionAutoCommit(session.isAutoCommit());
            // LAST_QUERY_ID must follow the SESSION, not the pool thread that happens to serve this
            // statement: consecutive requests of one session land on different threads, and a
            // thread-keyed history would lose the previous statement's ID between them.
            engine.getQueryResultCache().beginSessionScope(session.getSessionId());
            // ALTER SESSION parameters and SET variables follow the session too: one engine-wide store
            // let a TIMEZONE, a QUERY_TAG or a variable set in one session reach every other.
            engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());

            // ALWAYS restore (or clear, when null) this session's transaction. The shared engine uses a
            // thread-local "current transaction", so if we skipped this when the session has none, the
            // session would inherit the previous session's transaction and see its uncommitted writes —
            // breaking multi-session READ COMMITTED isolation.
            engine.getTransactionManager().setCurrentTransaction(session.getTransactionId());
        } catch (final Exception e) {
            logger.warn("Error applying session context: {}", e.getMessage());
        }
    }

    /**
     * Bind the session's JSON_INDENT to this thread for rendering a statement's rows after the statement's own
     * scope has closed — read from the session's own settings, which only its statements see. The caller clears
     * it with {@code VariantJsonFormat.clearSessionScope}.
     *
     * @param session the session whose rows are rendered
     */
    public void bindJsonIndent(final SessionContext session) {
        engine.getSecurityManager().getSessionContext().bindSettings(session.getSettings());
        try {
            engine.bindJsonIndent();
        } finally {
            engine.getSecurityManager().getSessionContext().unbindSettings();
        }
    }

    /**
     * Capture current engine state back to session
     */
    private void captureSessionContext(final SessionContext session) {
        try {
            session.setCurrentDatabase(engine.getCurrentDatabase());
            session.setCurrentSchema(engine.getCurrentSchema());
            session.setCurrentWarehouse(engine.getCatalog().getCurrentWarehouse());
            session.setAutoCommit(engine.isAutoCommit());
            // An aborted transaction the session has not acknowledged is still the session's to end.
            session.setInTransaction(engine.getTransactionManager().hasActiveTransaction()
                || engine.getTransactionManager().getAbortedTransaction() != null);
            session.setTransactionId(engine.getTransactionManager().getCurrentTransactionId());
        } catch (final Exception e) {
            logger.warn("Error capturing session context: {}", e.getMessage());
        }
    }

    // Session management

    public SessionContext createSession() {
        return sessionManager.createSession();
    }

    public SessionContext getOrCreateSession(final String sessionId) {
        engine.getSessionDirectory().endAborted();
        return sessionManager.getOrCreateSession(sessionId);
    }

    /** A live session, or null: a session SYSTEM$ABORT_SESSION ended is none. */
    public SessionContext getSession(final String sessionId) {
        engine.getSessionDirectory().endAborted();
        return sessionManager.getSession(sessionId);
    }

    public void removeSession(final String sessionId) {
        final AbortableHttpSession entry = abortable.remove(sessionId);
        if (entry != null) {
            engine.getSessionDirectory().unregister(entry.sessionNumber());
        }
        sessionStates.remove(sessionId);
        engine.getQueryResultCache().forgetSession(sessionId);
        sessionManager.removeSession(sessionId);
    }

    /**
     * End a session for good: roll back the transaction it left open, drop what it created TEMPORARY, then drop
     * everything the engine kept for it. Both a released session and an expired one end this way, so neither can
     * leave a transaction running with nobody to end it, nor a temporary table or function for the sessions after
     * it to find.
     *
     * @param session the session to end
     */
    public void endSession(final SessionContext session) {
        if (session.isInTransaction()) {
            try {
                // Through execute(), under the engine's lock like any request: the idle sweep's thread is not
                // a request thread.
                execute("ROLLBACK", session);
            } catch (final RuntimeException failed) {
                // The session goes regardless; its transaction's writes were never committed.
                logger.warn("Rolling back ended session {} failed: {}", session.getSessionId(), failed.getMessage());
            }
        }
        // What the session created TEMPORARY ends with it: under the engine's write lock, like the DROP it is.
        engineLock.writeLock().lock();
        try {
            engine.dropTemporaryObjectsOf(session.getSettings().getSessionNumber());
        } catch (final RuntimeException failed) {
            logger.warn("Dropping the temporary objects of ended session {} failed: {}", session.getSessionId(),
                failed.getMessage());
        } finally {
            engineLock.writeLock().unlock();
        }
        removeSession(session.getSessionId());
    }

    /**
     * End every session idle for longer than {@code idleMs} now, rather than at the next scheduled sweep.
     *
     * @param idleMs how long a session may sit idle
     * @return how many sessions were ended
     */
    public int expireIdleSessions(final long idleMs) {
        return sessionManager.expireSessionsIdleFor(idleMs);
    }

    /** How long a session may sit idle before it is ended, in milliseconds; 0 when sessions never idle out. */
    public long getSessionIdleTimeoutMs() {
        return sessionManager.getSessionTimeoutMs();
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
    }

    /**
     * The local file a stage file URL names — BUILD_STAGE_FILE_URL's {@code /api/files/<database>/<schema>/<stage>/<path>}
     * with each name canonical — found under the engine's read lock, so no statement changes the stage meanwhile.
     *
     * @param database the database, canonical
     * @param schema   the schema, canonical
     * @param stage    the stage's name, canonical
     * @param relative the file's stage-relative name
     * @return the file's local path, or null when no named stage has those names or it has no local directory
     */
    public Path stageFileUrlTarget(final String database, final String schema, final String stage,
                                   final String relative) {
        engineLock.readLock().lock();
        try {
            final NamedStage named = engine.getExecutor().namedStage(database, schema, stage);
            return named == null ? null : named.fileAt(relative);
        } finally {
            engineLock.readLock().unlock();
        }
    }

    // Component access

    public DatabaseEngine getEngine() {
        return engine;
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    /**
     * Shutdown the concurrent engine
     */
    public void shutdown() {
        logger.info("Shutting down concurrent Frostlake engine");
        sessionManager.shutdown();
        engine.shutdown();
        sessionStates.clear();
    }

}
