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

import dev.frostlake.http.SessionContext;
import dev.frostlake.http.SessionManager;
import dev.frostlake.storage.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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

    public ConcurrentDatabaseEngine() {
        this.engine = new DatabaseEngine();
        this.sessionManager = new SessionManager();
        // Non-fair: higher read throughput for this read-heavy engine. The JDK's non-fair RRWL still
        // blocks barging readers when a writer is queued, so writers are not badly starved.
        this.engineLock = new ReentrantReadWriteLock();
        this.sessionStates = new ConcurrentHashMap<>();
        logger.info("Initialized concurrent Frostlake engine");
    }

    /**
     * Execute SQL in a session context
     */
    public ExecutionResult execute(final String sql, final SessionContext session) {
        if (session == null) {
            throw new IllegalArgumentException("Session context is required");
        }

        session.touch();

        // Synchronize on session to prevent concurrent modifications to same session
        SessionState state = sessionStates.computeIfAbsent(
            session.getSessionId(),
            (final var k) -> new SessionState()
        );

        synchronized (state) {
            try {
                // Read lock for SELECT queries, write lock for DDL/DML
                if (isReadOnlyQuery(sql)) {
                    engineLock.readLock().lock();
                } else {
                    engineLock.writeLock().lock();
                }

                try {
                    // Apply session context before execution (inside lock for thread safety)
                    applySessionContext(session);

                    long startTime = System.currentTimeMillis();
                    ExecutionResult result = engine.execute(sql);
                    long duration = System.currentTimeMillis() - startTime;

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
                    if (isReadOnlyQuery(sql)) {
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
        ExecutionResult result = execute(sql, session);
        if (!result.isSuccess()) {
            throw new RuntimeException("Query execution failed: " + result.getErrorMessage());
        }
        if (result.getResultSets().isEmpty()) {
            return new ResultSet(List.of());
        }
        return result.getResultSets().get(0);
    }

    /**
     * Check if query is read-only (can use read lock)
     */
    private boolean isReadOnlyQuery(final String sql) {
        String upperSql = sql.trim().toUpperCase();
        return upperSql.startsWith("SELECT") ||
               upperSql.startsWith("SHOW") ||
               upperSql.startsWith("DESCRIBE") ||
               upperSql.startsWith("DESC") ||
               upperSql.startsWith("EXPLAIN");
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
            engine.getCatalog().beginSessionScope(session.getCurrentDatabase(), session.getCurrentSchema());
            engine.getTransactionManager().beginSessionAutoCommit(session.isAutoCommit());

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
     * Capture current engine state back to session
     */
    private void captureSessionContext(final SessionContext session) {
        try {
            session.setCurrentDatabase(engine.getCurrentDatabase());
            session.setCurrentSchema(engine.getCurrentSchema());
            session.setAutoCommit(engine.isAutoCommit());
            session.setInTransaction(engine.getTransactionManager().hasActiveTransaction());
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
        return sessionManager.getOrCreateSession(sessionId);
    }

    public SessionContext getSession(final String sessionId) {
        return sessionManager.getSession(sessionId);
    }

    public void removeSession(final String sessionId) {
        sessionStates.remove(sessionId);
        sessionManager.removeSession(sessionId);
    }

    public int getActiveSessionCount() {
        return sessionManager.getActiveSessionCount();
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

    /**
     * Per-session state synchronization object
     */
    private static class SessionState {
        // Empty class used as synchronization monitor
        // Ensures operations within same session are serialized
    }
}
