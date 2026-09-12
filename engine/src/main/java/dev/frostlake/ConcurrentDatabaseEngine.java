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
import dev.frostlake.http.SessionContext;
import dev.frostlake.http.SessionManager;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
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
        this(new DatabaseEngine());
    }

    /** A concurrent wrapper honoring the given configuration (database.default / schema.default). */
    public ConcurrentDatabaseEngine(final EngineConfig config) {
        this(new DatabaseEngine(config));
    }

    private ConcurrentDatabaseEngine(final DatabaseEngine engine) {
        this.engine = engine;
        // New sessions start where the engine's configuration points, exactly like the engine's
        // own bootstrap session.
        this.sessionManager = new SessionManager(
            engine.getConfig().getDefaultDatabase().toUpperCase(),
            engine.getConfig().getDefaultSchema().toUpperCase());
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
                    final ExecutionResult result = engine.execute(sql);
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
     * 'SELECT seq1.NEXTVAL' (the sequence advances). Anything unparseable classifies as a WRITE,
     * so the exclusive lock is the failure mode.
     */
    private boolean isReadOnlyQuery(final String sql) {
        try {
            final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(sql));
            final CommonTokenStream tokens = new CommonTokenStream(lexer);
            final FrostlakeParser parser = new FrostlakeParser(tokens);
            final boolean[] failed = new boolean[1];
            final BaseErrorListener silent = new BaseErrorListener() {
                @Override
                public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol,
                        final int line, final int charPositionInLine, final String msg,
                        final RecognitionException e) {
                    failed[0] = true;
                }
            };
            lexer.removeErrorListeners();
            lexer.addErrorListener(silent);
            parser.removeErrorListeners();
            parser.addErrorListener(silent);
            final FrostlakeParser.SqlScriptContext script = parser.sqlScript();
            if (failed[0] || script.flowChain().isEmpty()) {
                return false;
            }
            for (final Token token : tokens.getTokens()) {
                if (token.getType() == FrostlakeLexer.NEXTVAL) {
                    return false;   // seq.NEXTVAL advances the sequence even inside a SELECT
                }
            }
            for (final FrostlakeParser.FlowChainContext chain : script.flowChain()) {
                for (final FrostlakeParser.StatementContext stmt : chain.statement()) {
                    if (!isReadOnlyStatement(stmt)) {
                        return false;
                    }
                }
            }
            return true;
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /** A query (SELECT / WITH, however wrapped), EXPLAIN, SHOW or DESCRIBE — nothing else reads only. */
    private boolean isReadOnlyStatement(final FrostlakeParser.StatementContext stmt) {
        if (stmt.queryStatement() != null || stmt.explainStatement() != null) {
            return true;
        }
        final int first = stmt.getStart().getType();
        return first == FrostlakeParser.SHOW || first == FrostlakeParser.DESCRIBE
            || first == FrostlakeParser.DESC;
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
            // LAST_QUERY_ID must follow the SESSION, not the pool thread that happens to serve this
            // statement: consecutive requests of one session land on different threads, and a
            // thread-keyed history would lose the previous statement's ID between them.
            engine.getQueryResultCache().beginSessionScope(session.getSessionId());

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
        engine.getQueryResultCache().forgetSession(sessionId);
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

}
