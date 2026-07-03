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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    private static final long DEFAULT_TIMEOUT_MS = 30 * 60 * 1000; // 30 minutes

    public SessionManager() {
        this(DEFAULT_TIMEOUT_MS);
    }

    public SessionManager(final long sessionTimeoutMs) {
        this.sessions = new ConcurrentHashMap<>();
        this.sessionTimeoutMs = sessionTimeoutMs;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor((final var r) -> {
            Thread t = new Thread(r, "SessionCleanup");
            t.setDaemon(true);
            return t;
        });

        // Start cleanup task to remove expired sessions
        startCleanupTask();
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate(() -> {
            try {
                cleanupExpiredSessions();
            } catch (final Exception e) {
                logger.error("Error during session cleanup", e);
            }
        }, 1, 5, TimeUnit.MINUTES);
    }

    /**
     * Create a new session
     */
    public SessionContext createSession() {
        SessionContext context = new SessionContext();
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

        SessionContext context = sessions.get(sessionId);
        if (context == null) {
            context = new SessionContext(sessionId);
            sessions.put(sessionId, context);
            logger.info("Created session with provided ID: {}", sessionId);
        } else {
            context.touch();
        }
        return context;
    }

    /**
     * Get existing session
     */
    public SessionContext getSession(final String sessionId) {
        SessionContext context = sessions.get(sessionId);
        if (context != null) {
            context.touch();
        }
        return context;
    }

    /**
     * Remove a session
     */
    public void removeSession(final String sessionId) {
        SessionContext removed = sessions.remove(sessionId);
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
