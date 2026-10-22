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

package dev.frostlake.persistence;

import dev.frostlake.metastore.QueryHistory;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A statement a time-travel point may name by its query ID, as a restored state keeps it: its kind, when it began,
 * ended and its changes became visible, and the places those fell at in the order snapshots are taken in. An entry of
 * the points log beside the write-ahead log also names the logged statements its places are mapped through (see
 * {@link QueryPointLog}).
 */
public class QueryPointSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    public String queryId;
    public String queryType;
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public LocalDateTime visibleFrom;
    public long startSequence;
    public long visibleSequence;
    public boolean dynamicTableRefresh;
    /** The query ID the write-ahead log holds the statement under, or null. */
    public String loggedAs;
    /** In a points log, the logged statement the start's place is mapped through, or null for the place itself. */
    public String startThrough;
    /** In a points log, the logged statement the visible place is mapped through, or null for the place itself. */
    public String visibleThrough;

    /**
     * A statement as a snapshot keeps it.
     *
     * @param queryId   the query ID a point names it by
     * @param statement the statement
     * @return its snapshot
     */
    public static QueryPointSnapshot of(final String queryId, final QueryHistory statement) {
        final QueryPointSnapshot saved = new QueryPointSnapshot();
        saved.queryId = queryId;
        saved.queryType = statement.getQueryType();
        saved.startTime = statement.getStartTime();
        saved.endTime = statement.getEndTime();
        saved.visibleFrom = statement.getVisibleFrom();
        saved.startSequence = statement.getStartSequence();
        saved.visibleSequence = statement.getVisibleSequence();
        saved.dynamicTableRefresh = statement.isDynamicTableRefresh();
        saved.loggedAs = statement.getLoggedAs();
        return saved;
    }

    /**
     * The statement this snapshot keeps, at the places given.
     *
     * @param start   the place it began at
     * @param visible the place its changes became visible at, or -1
     * @return the statement
     */
    public QueryHistory toHistory(final long start, final long visible) {
        return QueryHistory.restoredPoint(queryId, queryType, startTime, endTime, visibleFrom, start, visible,
            dynamicTableRefresh, loggedAs);
    }

    /**
     * The statement this snapshot keeps, at the places it kept.
     *
     * @return the statement
     */
    public QueryHistory toHistory() {
        return toHistory(startSequence, visibleSequence);
    }
}
