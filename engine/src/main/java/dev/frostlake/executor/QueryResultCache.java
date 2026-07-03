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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cache for query results, storing them by query ID
 * Supports RESULT_SCAN table function
 */
public class QueryResultCache {

    private static final int DEFAULT_MAX_CACHED_RESULTS = 500;

    private final int maxCachedResults;
    private final Map<String, CachedResult> cache;
    // Per-thread query ID history so concurrent sessions don't overwrite each other
    // Index 0 = most recent, index 1 = one before, etc.
    private final ThreadLocal<ArrayDeque<String>> queryIdHistory =
        ThreadLocal.withInitial(ArrayDeque::new);
    private static final int MAX_ID_HISTORY = 20;

    public QueryResultCache() {
        this(DEFAULT_MAX_CACHED_RESULTS);
    }

    public QueryResultCache(final int maxCachedResults) {
        this.maxCachedResults = maxCachedResults;
        // Use LinkedHashMap with access order for LRU behavior
        this.cache = new LinkedHashMap<String, CachedResult>(maxCachedResults + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<String, CachedResult> eldest) {
                return size() > maxCachedResults;
            }
        };
    }

    /**
     * Store a query result with a generated query ID
     * @param sql The SQL statement executed
     * @param result The result set
     * @return The generated query ID
     */
    public String cacheResult(final String sql, final ResultSet result) {
        String queryId = generateQueryId();
        cache.put(queryId, new CachedResult(sql, result));
        pushQueryId(queryId);
        return queryId;
    }

    /**
     * Retrieve a cached result by query ID
     * @param queryId The query ID
     * @return The cached result, or null if not found
     */
    public ResultSet getResult(final String queryId) {
        CachedResult cached = cache.get(queryId);
        return cached != null ? cached.result : null;
    }

    /**
     * Get the SQL for a cached query
     * @param queryId The query ID
     * @return The SQL statement, or null if not found
     */
    public String getSql(final String queryId) {
        CachedResult cached = cache.get(queryId);
        return cached != null ? cached.sql : null;
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
     *   Index 0 is treated as -1 for compatibility.
     */
    /**
     * Get a query ID by relative index. Snowflake semantics:
     *   -1 = most recent (default), -2 = one before that, etc.
     *   0 is treated as -1 (most recent) for compatibility.
     */
    public String getQueryId(final int index) {
        ArrayDeque<String> history = queryIdHistory.get();
        if (history.isEmpty()) return null;
        // -1 → offset 0 (newest), -2 → offset 1, 0 → offset 0
        int offset = (index >= 0) ? 0 : (-index - 1);
        if (offset >= history.size()) return null;
        int i = 0;
        for (final String id : history) {
            if (i++ == offset) return id;
        }
        return null;
    }

    /**
     * Check if a query ID exists in the cache
     * @param queryId The query ID
     * @return true if the result is cached
     */
    public boolean hasResult(final String queryId) {
        return cache.containsKey(queryId);
    }

    /**
     * Clear all cached results
     */
    public void clear() {
        cache.clear();
        queryIdHistory.remove();
    }

    /**
     * Get the number of cached results
     * @return The cache size
     */
    public int size() {
        return cache.size();
    }

    /**
     * Generate a query ID for a SQL statement without caching a result
     * Used for DML statements that don't return result sets
     * @param sql The SQL statement
     * @return The generated query ID
     */
    public String generateQueryId(final String sql) {
        String queryId = generateQueryId();
        pushQueryId(queryId);
        return queryId;
    }

    private void pushQueryId(final String queryId) {
        ArrayDeque<String> history = queryIdHistory.get();
        history.addFirst(queryId);
        while (history.size() > MAX_ID_HISTORY) {
            history.removeLast();
        }
    }

    /**
     * Generate a unique query ID
     * Format similar to Snowflake: 01b1e2f3-0001-abcd-0000-000012345678
     */
    private String generateQueryId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Internal class to hold cached query results
     */
    private static class CachedResult {
        final String sql;
        final ResultSet result;

        CachedResult(final String sql, final ResultSet result) {
            this.sql = sql;
            this.result = result;
        }
    }
}
