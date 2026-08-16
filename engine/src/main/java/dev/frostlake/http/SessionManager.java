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
 */
public class SessionManager {
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final Map<String, SessionContext> sessions;
    private final long sessionTimeoutMs;
    private final ScheduledExecutorService cleanupExecutor;
    private final String defaultDatabase;
    private final String defaultSchema;

    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

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

        // Start cleanup task to remove expired sessions
        startCleanupTask();
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    cleanupExpiredSessions();
                } catch (final Exception e) {
                    logger.error("Error during session cleanup", e);
                }
            }
        }, 1, 5, TimeUnit.MINUTES);
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
        int removed = 0;
        for (final Map.Entry<String, SessionContext> entry : sessions.entrySet()) {
            if (entry.getValue().isExpired(sessionTimeoutMs)) {
                sessions.remove(entry.getKey());
                removed++;
                logger.debug("Cleaned up expired session: {}", entry.getKey());
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired sessions", removed);
        }
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
