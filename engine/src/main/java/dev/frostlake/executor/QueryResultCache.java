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

package dev.frostlake.executor;

import dev.frostlake.storage.ResultSet;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Query results held by query ID so RESULT_SCAN can read them back, plus the per-session history of
 * query IDs that LAST_QUERY_ID walks.
 *
 * <p>The history is keyed by SESSION, not by thread. A session's statements are not guaranteed to run
 * on one thread — the HTTP server hands consecutive requests to whichever pool thread is free — so a
 * thread-keyed history loses the previous statement's ID between two requests of the same session,
 * and the {@code SHOW …} then {@code SELECT … FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))} pattern
 * fails with "No previous query results available" while working in-process. Callers that serve more
 * than one session bind the session around each statement with
 * {@link #beginSessionScope(String)} / {@link #clearSessionScope()}; anything else shares one
 * implicit session, which is what an embedded engine wants.
 *
 * <p>Cached RESULTS stay shared across sessions: a query ID is a handle anyone holding it can scan,
 * which is how Snowflake behaves. Only the history of "what did I run last" is per session.
 *
 * <p>A statement that FAILED gets an ID too, and LAST_QUERY_ID names it, but it has no result: those
 * IDs are remembered, bounded like the results, so RESULT_SCAN can refuse one with the reason.
 */
public class QueryResultCache {

    private static final int DEFAULT_MAX_CACHED_RESULTS = 500;
    private static final int MAX_ID_HISTORY = 20;

    /** How many sessions keep a history before the least recently used one is dropped. */
    private static final int MAX_TRACKED_SESSIONS = 256;

    /** The history every caller shares when no session is bound (an embedded engine, a test). */
    private static final String IMPLICIT_SESSION = "";

    private final int maxCachedResults;
    private final Map<String, CachedResult> cache;
    private final Map<String, Deque<String>> historyBySession;
    private final Map<String, Boolean> failedQueryIds;
    private final ThreadLocal<String> boundSession = new ThreadLocal<>();

    public QueryResultCache() {
        this(DEFAULT_MAX_CACHED_RESULTS);
    }

    public QueryResultCache(final int maxCachedResults) {
        this.maxCachedResults = maxCachedResults;
        // Access-ordered for LRU behaviour. Every read and write goes through a synchronized block:
        // in access order even a get() mutates the map, and statements from different sessions run
        // concurrently under the engine's read lock.
        this.cache = new LinkedHashMap<String, CachedResult>(maxCachedResults + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, CachedResult> eldest) {
                return size() > maxCachedResults;
            }
        };
        this.historyBySession = new LinkedHashMap<String, Deque<String>>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Deque<String>> eldest) {
                return size() > MAX_TRACKED_SESSIONS;
            }
        };
        this.failedQueryIds = new LinkedHashMap<String, Boolean>(16, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, Boolean> eldest) {
                return size() > maxCachedResults;
            }
        };
    }

    /**
     * Bind {@code sessionId} to the calling thread for the duration of one statement, so the query
     * IDs it records land in that session's history. Pair with {@link #clearSessionScope()} in a
     * finally block — pooled threads outlive the statements they serve.
     */
    public void beginSessionScope(final String sessionId) {
        boundSession.set(sessionId != null ? sessionId : IMPLICIT_SESSION);
    }

    /** Drop this thread's session binding. Idempotent. */
    public void clearSessionScope() {
        boundSession.remove();
    }

    /** Forget a session's query-ID history, once that session is gone. */
    public void forgetSession(final String sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (historyBySession) {
            historyBySession.remove(sessionId);
        }
    }

    /**
     * Store a query result with a generated query ID
     * @param sql The SQL statement executed
     * @param result The result set
     * @return The generated query ID
     */
    public String cacheResult(final String sql, final ResultSet result) {
        final String queryId = generateQueryId();
        synchronized (cache) {
            cache.put(queryId, new CachedResult(sql, result));
        }
        pushQueryId(queryId);
        return queryId;
    }

    /**
     * Retrieve a cached result by query ID
     * @param queryId The query ID
     * @return The cached result, or null if not found
     */
    public ResultSet getResult(final String queryId) {
        synchronized (cache) {
            final CachedResult cached = cache.get(queryId);
            return cached != null ? cached.getResult() : null;
        }
    }

    /**
     * Get the SQL for a cached query
     * @param queryId The query ID
     * @return The SQL statement, or null if not found
     */
    public String getSql(final String queryId) {
        synchronized (cache) {
            final CachedResult cached = cache.get(queryId);
            return cached != null ? cached.getSql() : null;
        }
    }

    /**
     * Get the last query ID
     * @return The most recent query ID, or null if no queries have been executed
     */
    public String getLastQueryId() {
        return getQueryId(-1);
    }

    /**
     * Get a query ID by relative index. Snowflake semantics:
     *   -1 = most recent (default), -2 = one before that, etc.
     *   0 is treated as -1 (most recent) for compatibility.
     */
    public String getQueryId(final int index) {
        final Deque<String> history = historyForCurrentSession(false);
        if (history == null || history.isEmpty()) {
            return null;
        }
        // -1 → offset 0 (newest), -2 → offset 1, 0 → offset 0
        final int offset = index >= 0 ? 0 : -index - 1;
        synchronized (historyBySession) {
            if (offset >= history.size()) {
                return null;
            }
            int i = 0;
            for (final String id : history) {
                if (i++ == offset) {
                    return id;
                }
            }
        }
        return null;
    }

    /**
     * Check if a query ID exists in the cache
     * @param queryId The query ID
     * @return true if the result is cached
     */
    public boolean hasResult(final String queryId) {
        synchronized (cache) {
            return cache.containsKey(queryId);
        }
    }

    /**
     * Clear all cached results
     */
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
        synchronized (historyBySession) {
            historyBySession.clear();
        }
        synchronized (failedQueryIds) {
            failedQueryIds.clear();
        }
    }

    /**
     * Get the number of cached results
     * @return The cache size
     */
    public int size() {
        synchronized (cache) {
            return cache.size();
        }
    }

    /**
     * Generate a query ID for a SQL statement without caching a result
     * Used for DML statements that don't return result sets
     * @param sql The SQL statement
     * @return The generated query ID
     */
    public String generateQueryId(final String sql) {
        final String queryId = generateQueryId();
        pushQueryId(queryId);
        return queryId;
    }

    /**
     * Generate the query ID of a statement that FAILED. It enters the session's history like any other,
     * so LAST_QUERY_ID names it, and is remembered as failed, so RESULT_SCAN refuses it with the reason.
     *
     * @param sql the SQL statement
     * @return the generated query ID
     */
    public String generateFailedQueryId(final String sql) {
        final String queryId = generateQueryId(sql);
        synchronized (failedQueryIds) {
            failedQueryIds.put(queryId, Boolean.TRUE);
        }
        return queryId;
    }

    /**
     * Whether a query ID names a statement that failed.
     *
     * @param queryId the query ID
     * @return true when the statement it names failed
     */
    public boolean hasFailed(final String queryId) {
        synchronized (failedQueryIds) {
            return failedQueryIds.containsKey(queryId);
        }
    }

    private void pushQueryId(final String queryId) {
        synchronized (historyBySession) {
            final Deque<String> history = historyForCurrentSession(true);
            history.addFirst(queryId);
            while (history.size() > MAX_ID_HISTORY) {
                history.removeLast();
            }
        }
    }

    /**
     * The calling thread's session history, creating it when {@code create} is set. Callers that
     * iterate the returned deque must hold the {@code historyBySession} monitor while they do.
     */
    private Deque<String> historyForCurrentSession(final boolean create) {
        final String sessionId = boundSession.get();
        final String key = sessionId != null ? sessionId : IMPLICIT_SESSION;
        synchronized (historyBySession) {
            final Deque<String> history = historyBySession.get(key);
            if (history != null || !create) {
                return history;
            }
            final Deque<String> created = new ArrayDeque<>();
            historyBySession.put(key, created);
            return created;
        }
    }

    /**
     * Generate a unique query ID
     * Format similar to Snowflake: 01b1e2f3-0001-abcd-0000-000012345678
     */
    private String generateQueryId() {
        return UUID.randomUUID().toString();
    }
}
