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

package dev.frostlake.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Manages HTTP sessions for concurrent users
 * Handles session creation, retrieval, and cleanup
 *
 * <p>A session that sits idle longer than the timeout is ended, as Snowflake ends one after four hours without
 * activity: every request that names it, a client's heartbeat included, counts as activity. A timeout of 0 keeps
 * every session until it is released.
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, SessionContext> sessions;
    private final long sessionTimeoutMs;
    private volatile ExpiredSessionListener expiredSessionListener;
    private final ScheduledExecutorService cleanupExecutor;
    private final String defaultDatabase;
    private final String defaultSchema;

    /** Snowflake's four hours: a session policy's SESSION_IDLE_TIMEOUT_MINS defaults to 240. */
    private static final long DEFAULT_TIMEOUT_MS = 4L * 60 * 60 * 1000;
    /** The idle sweep runs at most this far apart, and a quarter of the timeout apart when that is sooner. */
    private static final long MAX_SWEEP_MS = 5L * 60 * 1000;
    private static final long MIN_SWEEP_MS = 1000L;

    public SessionManager() {
        this(DEFAULT_TIMEOUT_MS);
    }

    public SessionManager(final long sessionTimeoutMs) {
        this("SNOWFLAKE", "PUBLIC", sessionTimeoutMs);
    }

    /** A manager whose new sessions start in the server's configured default database/schema. */
    public SessionManager(final String defaultDatabase, final String defaultSchema) {
        this(defaultDatabase, defaultSchema, DEFAULT_TIMEOUT_MS);
    }

    public SessionManager(final String defaultDatabase, final String defaultSchema,
                          final long sessionTimeoutMs) {
        this.defaultDatabase = defaultDatabase;
        this.defaultSchema = defaultSchema;
        this.sessions = new ConcurrentHashMap<>();
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                final Thread t = new Thread(r, "SessionCleanup");
                t.setDaemon(true);
                return t;
            }
        });

        // Start cleanup task to remove expired sessions; with no timeout there is nothing to sweep.
        if (sessionTimeoutMs > 0) {
            startCleanupTask(sweepPeriodMs(sessionTimeoutMs));
        }
    }

    /**
     * How far apart the idle sweep runs for a timeout: a quarter of it, between a second and five minutes, so a
     * session ends within a quarter of its timeout of going idle.
     *
     * @param timeoutMs the idle timeout
     * @return the period, in milliseconds
     */
    static long sweepPeriodMs(final long timeoutMs) {
        return Math.max(MIN_SWEEP_MS, Math.min(MAX_SWEEP_MS, timeoutMs / 4));
    }

    /** How long a session may sit idle before it is ended, in milliseconds; 0 when sessions never idle out. */
    public long getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    private void startCleanupTask(final long periodMs) {
        cleanupExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredSessions();
                } catch (final Exception e) {
                    logger.error("Error during session cleanup", e);
                }
            }
        }, periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Create a new session
     */
    public SessionContext createSession() {
        final SessionContext context = new SessionContext(
            UUID.randomUUID().toString(), defaultDatabase, defaultSchema);
        sessions.put(context.getSessionId(), context);
        logger.info("Created new session: {}", context.getSessionId());
        return context;
    }

    /**
     * Get or create session by ID
     */
    public SessionContext getOrCreateSession(final String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return createSession();
        }

        final SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.touch();
            return context;
        }
        // putIfAbsent: two first requests racing on one id must end up in ONE session.
        final SessionContext created = new SessionContext(sessionId, defaultDatabase, defaultSchema);
        final SessionContext raced = sessions.putIfAbsent(sessionId, created);
        if (raced != null) {
            raced.touch();
            return raced;
        }
        logger.info("Created session with provided ID: {}", sessionId);
        return created;
    }

    /**
     * Get existing session
     */
    public SessionContext getSession(final String sessionId) {
        final SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.touch();
        }
        return context;
    }

    /**
     * Remove a session
     */
    public void removeSession(final String sessionId) {
        final SessionContext removed = sessions.remove(sessionId);
        if (removed != null) {
            logger.info("Removed session: {}", sessionId);
        }
    }

    /**
     * Clean up expired sessions
     */
    private void cleanupExpiredSessions() {
        expireSessionsIdleFor(sessionTimeoutMs);
    }

    /**
     * End every session idle for longer than {@code idleMs}: each is removed from the map and then handed
     * to the {@link ExpiredSessionListener}, which rolls back its open transaction and drops the engine's
     * state for it — what releasing a session does. Removing the map entry alone used to leave an expired
     * session's transaction running, listed in SHOW TRANSACTIONS with its locks held, for the server's life.
     *
     * @param idleMs how long a session may sit idle before it is ended
     * @return how many sessions were ended
     */
    public int expireSessionsIdleFor(final long idleMs) {
        int removed = 0;
        for (final Map.Entry<String, SessionContext> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(idleMs) && sessions.remove(entry.getKey(), entry.getValue())) {
                removed++;
                logger.debug("Cleaned up expired session: {}", entry.getKey());
                final ExpiredSessionListener listener = expiredSessionListener;
                if (listener != null) {
                    try {
                        listener.expired(entry.getValue());
                    } catch (final RuntimeException failed) {
                        // One session's cleanup must not stop the sweep from ending the rest.
                        logger.warn("Ending expired session {} failed: {}", entry.getKey(), failed.getMessage());
                    }
                }
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired sessions", removed);
        }
        return removed;
    }

    /**
     * Who is told about each expired session; the engine that owns the sessions registers itself here.
     *
     * @param listener the listener, or null for none
     */
    public void setExpiredSessionListener(final ExpiredSessionListener listener) {
        this.expiredSessionListener = listener;
    }

    /**
     * Get active session count
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }

    /**
     * Shutdown the session manager
     */
    public void shutdown() {
        logger.info("Shutting down session manager");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (final InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        sessions.clear();
    }
}
