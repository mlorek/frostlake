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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks query execution history for the entire Frostlake engine instance
 */
public class QueryHistoryTracker {
    private final ConcurrentLinkedQueue<QueryHistory> queryHistory;
    /** The statements that have begun and not ended, in the order they began. */
    private final ConcurrentLinkedQueue<QueryHistory> running = new ConcurrentLinkedQueue<>();
    /** The replays of a former engine's logged statements, by the query ID each first answered with. */
    private final Map<String, QueryHistory> anchors = new ConcurrentHashMap<>();
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
     * Note a statement that has begun: it is listed as RUNNING until it ends.
     *
     * @param query the statement's row
     */
    public void begin(final QueryHistory query) {
        running.add(query);
    }

    /**
     * Note a statement that is over, whether or not it joined the history.
     *
     * @param query the statement's row
     */
    public void end(final QueryHistory query) {
        running.remove(query);
    }

    /**
     * The statements running now, oldest first.
     *
     * @return their rows
     */
    public List<QueryHistory> getRunning() {
        return new ArrayList<>(running);
    }

    /**
     * Add a query to history
     */
    public void addQuery(final QueryHistory query) {
        running.remove(query);
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
     * Get query by ID: a query this engine ran, or one a former engine ran that the write-ahead log's replay ran again
     * (see {@link #anchor}).
     */
    public QueryHistory getQueryById(final String queryId) {
        for (final QueryHistory query : queryHistory) {
            if (query.getQueryId().equals(queryId)) {
                return query;
            }
        }
        return queryId == null ? null : anchors.get(queryId);
    }

    /**
     * Lets a query ID a former engine answered a logged statement with name the statement's replay, for the
     * time-travel points that name it: kept whatever {@link #forgetSession} forgets of the replay's sessions, so a
     * statement run after the restart reads at that point too.
     *
     * @param originalId the ID the statement first answered with
     * @param replayed   its replay's history, or null when none was recorded
     */
    public void anchor(final String originalId, final QueryHistory replayed) {
        if (originalId != null && replayed != null) {
            anchors.put(originalId, replayed);
        }
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
     * Forget every query one session ran: a session that is over and that no listing may show, as the replay of
     * the write-ahead log at startup is.
     *
     * @param sessionId the session's number
     */
    public void forgetSession(final long sessionId) {
        final Iterator<QueryHistory> queries = queryHistory.iterator();
        while (queries.hasNext()) {
            final Long ran = queries.next().getSessionId();
            if (ran != null && ran.longValue() == sessionId) {
                queries.remove();
            }
        }
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
