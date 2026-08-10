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

package dev.frostlake.metastore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks query execution history for the entire Frostlake engine instance
 */
public class QueryHistoryTracker {
    private final ConcurrentLinkedQueue<QueryHistory> queryHistory;
    private final int maxHistorySize;
    private static final int DEFAULT_MAX_HISTORY_SIZE = 10000;

    public QueryHistoryTracker() {
        this(DEFAULT_MAX_HISTORY_SIZE);
    }

    public QueryHistoryTracker(final int maxHistorySize) {
        this.queryHistory = new ConcurrentLinkedQueue<>();
        this.maxHistorySize = maxHistorySize;
    }

    /**
     * Add a query to history
     */
    public void addQuery(final QueryHistory query) {
        queryHistory.add(query);

        // Maintain max size by removing oldest entries
        while (queryHistory.size() > maxHistorySize) {
            queryHistory.poll();
        }
    }

    /**
     * Get all query history
     */
    public List<QueryHistory> getAllHistory() {
        return new ArrayList<>(queryHistory);
    }

    /**
     * Get query history with limit
     */
    public List<QueryHistory> getHistory(final int limit) {
        final List<QueryHistory> result = new ArrayList<>();
        int count = 0;

        // Return most recent queries first (reverse order)
        final List<QueryHistory> allHistory = getAllHistory();
        Collections.reverse(allHistory);

        for (final QueryHistory query : allHistory) {
            if (count >= limit) break;
            result.add(query);
            count++;
        }

        return result;
    }

    /**
     * Get query by ID
     */
    public QueryHistory getQueryById(final String queryId) {
        for (final QueryHistory query : queryHistory) {
            if (query.getQueryId().equals(queryId)) {
                return query;
            }
        }
        return null;
    }

    /**
     * Get history filtered by user
     */
    public List<QueryHistory> getHistoryByUser(final String user) {
        final List<QueryHistory> result = new ArrayList<>();
        for (final QueryHistory query : queryHistory) {
            if (user.equalsIgnoreCase(query.getUser())) {
                result.add(query);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Get history filtered by database
     */
    public List<QueryHistory> getHistoryByDatabase(final String database) {
        final List<QueryHistory> result = new ArrayList<>();
        for (final QueryHistory query : queryHistory) {
            if (database != null && database.equalsIgnoreCase(query.getDatabase())) {
                result.add(query);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Get history filtered by query type
     */
    public List<QueryHistory> getHistoryByQueryType(final String queryType) {
        final List<QueryHistory> result = new ArrayList<>();
        for (final QueryHistory query : queryHistory) {
            if (queryType.equalsIgnoreCase(query.getQueryType())) {
                result.add(query);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Get history filtered by status
     */
    public List<QueryHistory> getHistoryByStatus(final String status) {
        final List<QueryHistory> result = new ArrayList<>();
        for (final QueryHistory query : queryHistory) {
            if (status.equalsIgnoreCase(query.getStatus())) {
                result.add(query);
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Clear all history
     */
    public void clear() {
        queryHistory.clear();
    }

    /**
     * Get current history size
     */
    public int size() {
        return queryHistory.size();
    }

    /**
     * Get the most recent query
     */
    public QueryHistory getLastQuery() {
        QueryHistory lastQuery = null;
        for (final QueryHistory query : queryHistory) {
            lastQuery = query;
        }
        return lastQuery;
    }
}
