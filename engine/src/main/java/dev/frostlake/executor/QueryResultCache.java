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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.storage.ResultSet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Query results held by query ID so RESULT_SCAN can read them back, plus the per-session history of
 * query IDs that LAST_QUERY_ID walks.
 *
 * <p>The history is keyed by SESSION, not by thread. A session's statements are not guaranteed to run
 * on one thread — the HTTP server hands consecutive requests to whichever pool thread is free — so a
 * thread-keyed history loses the previous statement's ID between two requests of the same session,
 * and the {@code SHOW …} then {@code SELECT … FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))} pattern
 * fails with "Statement NULL not found" while working in-process. Callers that serve more
 * than one session bind the session around each statement with
 * {@link #beginSessionScope(String)} / {@link #clearSessionScope()}; anything else shares one
 * implicit session, which is what an embedded engine wants.
 *
 * <p>Cached RESULTS stay shared across sessions: a query ID is a handle anyone holding it can scan,
 * which is how Snowflake behaves. What is KEPT is per session: each session keeps its own last results and its
 * whole history, so another session's statements never evict them — a session's LAST_QUERY_ID and RESULT_SCAN
 * answer the same whatever runs beside it. A session that ends hands its results to a pool of finished
 * sessions' results, still readable by their IDs, of the same bound.
 *
 * <p>A statement that FAILED gets an ID too, and LAST_QUERY_ID names it, but it has no result: those
 * IDs are remembered, bounded like the results, so RESULT_SCAN can refuse one with the reason.
 *
 * <p>The results are bounded by what they hold as well as by their number: past a budget of cells (a row's
 * values, counted over every row) the oldest results are let go, but each session's latest result stays, so
 * {@code RESULT_SCAN(LAST_QUERY_ID())} reads whatever ran last. The account keeps results in a store of its
 * own; an engine embedded in a test's process holds them on that process's heap.
 */
public class QueryResultCache {

    private static final int DEFAULT_MAX_CACHED_RESULTS = 500;

    /** How many of a session's first query IDs, and of its most recent ones, LAST_QUERY_ID can name. */
    private static final int MAX_ID_HISTORY = 1000;

    /**
     * How many sessions keep their history and results before the least recently active one is let go, as if it
     * had ended: a guard against sessions that never say they ended, far past any number running at once.
     */
    private static final int MAX_TRACKED_SESSIONS = 10_000;

    /** The history every caller shares when no session is bound (an embedded engine, a test). */
    private static final String IMPLICIT_SESSION = "";

    /** The cells held before older results are let go, unless the engine's configuration says otherwise. */
    public static final long DEFAULT_MAX_CACHED_CELLS = EngineConfig.DEFAULT_QUERY_RESULT_CACHE_CELLS;

    private final int maxCachedResults;
    private final long maxCachedCells;
    /** Every result held, by query ID. Guarded, with every map here, by this cache's own monitor. */
    private final Map<String, CachedResult> cache = new HashMap<String, CachedResult>();
    /** Query IDs a former engine answered with, each naming the result the write-ahead log's replay of it gave. */
    private final Map<String, String> replayedIds = new HashMap<String, String>();
    /** The cells each held result counts, by query ID, oldest first. */
    private final LinkedHashMap<String, Long> cellsHeld = new LinkedHashMap<String, Long>();
    /** The cells all held results count together. */
    private long heldCells;
    /** The IDs of each open session's results, oldest first, at most {@link #maxCachedResults} of them. */
    private final Map<String, Deque<String>> resultsBySession = new HashMap<String, Deque<String>>();
    /** The IDs of the results of sessions that ended, oldest first, at most {@link #maxCachedResults}. */
    private final Deque<String> finishedResults = new ArrayDeque<String>();
    private final Map<String, QueryIdHistory> historyBySession;
    private final Map<String, Boolean> failedQueryIds;
    private final ThreadLocal<String> boundSession = new ThreadLocal<>();

    public QueryResultCache() {
        this(DEFAULT_MAX_CACHED_RESULTS);
    }

    public QueryResultCache(final int maxCachedResults) {
        this(maxCachedResults, DEFAULT_MAX_CACHED_CELLS);
    }

    /**
     * A cache bounded by both measures.
     *
     * @param maxCachedResults how many results a session, and the finished sessions together, keep
     * @param maxCachedCells   how many cells all results together hold before the oldest are let go
     */
    public QueryResultCache(final int maxCachedResults, final long maxCachedCells) {
        this.maxCachedResults = maxCachedResults;
        this.maxCachedCells = maxCachedCells;
        // Every read and write goes through this cache's monitor: statements from different sessions run
        // concurrently under the engine's read lock, and the session map is access-ordered, so even a get()
        // mutates it.
        this.historyBySession = new LinkedHashMap<String, QueryIdHistory>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, QueryIdHistory> eldest) {
                if (size() <= MAX_TRACKED_SESSIONS) {
                    return false;
                }
                finish(eldest.getKey());
                return true;
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

    /**
     * The session bound to this thread, so work that runs as a session of its own can bind another and put this
     * one back afterwards.
     *
     * @return the bound session's id, or null when none is bound and the implicit session applies
     */
    public String boundSessionScope() {
        return boundSession.get();
    }

    /**
     * Forget a session's query-ID history, once that session is gone; its results join the finished sessions'
     * pool, still readable by their IDs.
     */
    public void forgetSession(final String sessionId) {
        if (sessionId == null) {
            return;
        }
        synchronized (this) {
            historyBySession.remove(sessionId);
            finish(sessionId);
        }
    }

    /** A session's results handed to the finished sessions' pool, its oldest results falling out past the bound. */
    private void finish(final String sessionId) {
        final Deque<String> own = resultsBySession.remove(sessionId);
        if (own == null) {
            return;
        }
        for (final String queryId : own) {
            finishedResults.addLast(queryId);
        }
        while (finishedResults.size() > maxCachedResults) {
            drop(finishedResults.removeFirst());
        }
    }

    /** Let one result go. */
    private void drop(final String queryId) {
        cache.remove(queryId);
        final Long cells = cellsHeld.remove(queryId);
        if (cells != null) {
            heldCells -= cells.longValue();
        }
    }

    /**
     * Let the oldest results go while all of them hold more cells than the budget, sparing each session's
     * latest.
     */
    private void keepWithinCells() {
        if (heldCells <= maxCachedCells) {
            return;
        }
        final Set<String> latest = new HashSet<String>();
        for (final Deque<String> own : resultsBySession.values()) {
            if (!own.isEmpty()) {
                latest.add(own.peekLast());
            }
        }
        final List<String> oldestFirst = new ArrayList<String>(cellsHeld.keySet());
        for (final String queryId : oldestFirst) {
            if (heldCells <= maxCachedCells) {
                return;
            }
            if (!latest.contains(queryId)) {
                drop(queryId);
            }
        }
    }

    /**
     * Store a query result with a generated query ID
     * @param sql The SQL statement executed
     * @param result The result set
     * @return The generated query ID
     */
    public String cacheResult(final String sql, final ResultSet result) {
        return cacheResult(sql, result, null);
    }

    /**
     * A query ID no statement has taken yet: the one a statement is listed under while it still runs, which it
     * then takes as it ends (see the three-argument {@link #cacheResult}).
     *
     * @return the reserved query ID
     */
    public String reserveQueryId() {
        return generateQueryId();
    }

    /**
     * Store a query result under the query ID reserved for its statement.
     *
     * @param sql      the SQL statement executed
     * @param result   the result set
     * @param reserved the ID from {@link #reserveQueryId}, or null to generate one
     * @return the query ID
     */
    public String cacheResult(final String sql, final ResultSet result, final String reserved) {
        final String queryId = reserved != null ? reserved : generateQueryId();
        synchronized (this) {
            cache.put(queryId, new CachedResult(sql, result));
            final long cells = cellsOf(result);
            final Long replaced = cellsHeld.put(queryId, Long.valueOf(cells));
            heldCells += cells - (replaced != null ? replaced.longValue() : 0L);
            // The session keeps its own last results: its oldest falls out, never another session's.
            final String key = currentSessionKey();
            Deque<String> own = resultsBySession.get(key);
            if (own == null) {
                own = new ArrayDeque<String>();
                resultsBySession.put(key, own);
            }
            own.addLast(queryId);
            while (own.size() > maxCachedResults) {
                drop(own.removeFirst());
            }
            keepWithinCells();
        }
        pushQueryId(queryId);
        return queryId;
    }

    /** The cells a result holds: each row's values, at least one per row. */
    private static long cellsOf(final ResultSet result) {
        if (result == null || result.getRows() == null) {
            return 0L;
        }
        final long width = Math.max(1, result.getColumns() == null ? 0 : result.getColumns().size());
        return width * result.getRows().size();
    }

    /**
     * Retrieve a cached result by query ID
     * @param queryId The query ID
     * @return The cached result, or null if not found
     */
    public ResultSet getResult(final String queryId) {
        synchronized (this) {
            final CachedResult cached = cached(queryId);
            return cached != null ? cached.getResult() : null;
        }
    }

    /** The result a query ID names: its own, or the one its replay gave (see {@link #alias}). */
    private CachedResult cached(final String queryId) {
        final CachedResult own = cache.get(queryId);
        if (own != null || queryId == null || !replayedIds.containsKey(queryId)) {
            return own;
        }
        return cache.get(replayedIds.get(queryId));
    }

    /**
     * Lets the query ID a former engine answered a query with name the result the write-ahead log's replay of that
     * query gave, so a logged RESULT_SCAN of that ID reads it again.
     *
     * @param originalId the ID the query first answered with
     * @param replayedId the ID its replay answered with
     */
    public void alias(final String originalId, final String replayedId) {
        if (originalId == null || replayedId == null || originalId.equals(replayedId)) {
            return;
        }
        synchronized (this) {
            replayedIds.put(originalId, replayedId);
        }
    }

    /**
     * Get the SQL for a cached query
     * @param queryId The query ID
     * @return The SQL statement, or null if not found
     */
    public String getSql(final String queryId) {
        synchronized (this) {
            final CachedResult cached = cached(queryId);
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
     * The query ID a LAST_QUERY_ID index names in the calling session (live-verified): -1 is the most
     * recent statement, -2 the one before it, and a positive index counts from the session's first
     * statement, 1 being the first. 0, and an index past either end of the session's history, name none.
     *
     * @param index the index
     * @return the query ID, or null
     */
    public String getQueryId(final int index) {
        synchronized (this) {
            final QueryIdHistory history = historyForCurrentSession(false);
            return history == null ? null : history.at(index);
        }
    }

    /**
     * Check if a query ID exists in the cache
     * @param queryId The query ID
     * @return true if the result is cached
     */
    public boolean hasResult(final String queryId) {
        synchronized (this) {
            return cache.containsKey(queryId);
        }
    }

    /**
     * Clear all cached results
     */
    public void clear() {
        synchronized (this) {
            cache.clear();
            resultsBySession.clear();
            finishedResults.clear();
            historyBySession.clear();
            failedQueryIds.clear();
        }
    }

    /**
     * Get the number of cached results
     * @return The cache size
     */
    public int size() {
        synchronized (this) {
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
        return generateQueryId(sql, null);
    }

    /**
     * {@link #generateQueryId(String)} for a statement with a reserved ID.
     *
     * @param sql      the SQL statement
     * @param reserved the ID from {@link #reserveQueryId}, or null to generate one
     * @return the query ID
     */
    public String generateQueryId(final String sql, final String reserved) {
        final String queryId = reserved != null ? reserved : generateQueryId();
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
        return generateFailedQueryId(sql, null);
    }

    /**
     * {@link #generateFailedQueryId(String)} for a statement with a reserved ID.
     *
     * @param sql      the SQL statement
     * @param reserved the ID from {@link #reserveQueryId}, or null to generate one
     * @return the query ID
     */
    public String generateFailedQueryId(final String sql, final String reserved) {
        final String queryId = generateQueryId(sql, reserved);
        synchronized (this) {
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
        synchronized (this) {
            return failedQueryIds.containsKey(queryId);
        }
    }

    private void pushQueryId(final String queryId) {
        synchronized (this) {
            historyForCurrentSession(true).add(queryId);
        }
    }

    /**
     * The calling thread's session history, creating it when {@code create} is set. Callers that
     * read or change the returned history must hold the {@code historyBySession} monitor while they do.
     */
    /** The key of the session the calling thread is bound to, the implicit one when none is. */
    private String currentSessionKey() {
        final String sessionId = boundSession.get();
        return sessionId != null ? sessionId : IMPLICIT_SESSION;
    }

    private QueryIdHistory historyForCurrentSession(final boolean create) {
        final String key = currentSessionKey();
        synchronized (this) {
            final QueryIdHistory history = historyBySession.get(key);
            if (history != null || !create) {
                return history;
            }
            final QueryIdHistory created = new QueryIdHistory(MAX_ID_HISTORY, MAX_ID_HISTORY);
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
