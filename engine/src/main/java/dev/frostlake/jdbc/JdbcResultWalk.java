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

package dev.frostlake.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * The walk a request's results take through {@code execute}, {@code getResultSet}, {@code getUpdateCount}
 * and {@code getMoreResults}, the way Snowflake's JDBC driver walks them. Every statement of the request
 * answers either ROWS, handed out as a result set, or an UPDATE COUNT: the rows a DML statement affected,
 * 0 for DDL and every other statement that answers no rows.
 *
 * <p>Live-verified, including the driver's one departure from the letter of {@code getMoreResults}: moving
 * onto an update count answers {@code true} as well whenever further results follow it, and answers
 * {@code false} only for the LAST result when that result is an update count, and past the end. So
 * {@code INSERT …; SELECT …; UPDATE …; CREATE …} walks as update count 1, then {@code true} and the rows,
 * then {@code true} with no result set and update count 1, then {@code false} and update count 0, then
 * {@code false} and -1.
 */
final class JdbcResultWalk {

    /** The vendor code and SQLSTATE the driver refuses a row-answering statement in executeUpdate with. */
    static final int NOT_AN_UPDATE_CODE = 200042;

    /** The vendor code the driver answers executeQuery with when a request's first result is a count. */
    static final int FIRST_RESULT_COUNT_CODE = 200048;

    /** How much of a statement's text the executeUpdate refusal quotes before it cuts it off. */
    private static final int QUOTED_TEXT_LENGTH = 20;

    /** Per result, in statement order: null for rows, else the update count. */
    private final List<Long> counts;
    private int position;

    /**
     * @param counts per result in statement order: null where the statement answers rows, else its count
     */
    JdbcResultWalk(final List<Long> counts) {
        this.counts = counts;
        this.position = 0;
    }

    /** A walk over no results — before the first execution, and after close. */
    static JdbcResultWalk none() {
        return new JdbcResultWalk(new ArrayList<Long>());
    }

    /** A walk over one update count — what a prepared batch leaves behind: the whole batch's count. */
    static JdbcResultWalk ofCount(final long count) {
        final List<Long> counts = new ArrayList<>();
        counts.add(Long.valueOf(count));
        return new JdbcResultWalk(counts);
    }

    /** The result the cursor stands on, or {@link #size()} past the end. */
    int position() {
        return position;
    }

    int size() {
        return counts.size();
    }

    /** Whether the current result answers rows — what {@code execute()} answers for the first. */
    boolean currentIsRows() {
        return position < counts.size() && counts.get(position) == null;
    }

    /** The current result's update count: -1 for rows, and past the end. */
    long updateCount() {
        if (position >= counts.size() || counts.get(position) == null) {
            return -1;
        }
        return counts.get(position).longValue();
    }

    /**
     * Move to the next result, answering what the driver's {@code getMoreResults()} answers: {@code true} for
     * rows, {@code true} for an update count with more results after it, {@code false} otherwise.
     *
     * @return the driver's answer
     */
    boolean next() {
        if (position < counts.size()) {
            position++;
        }
        if (position >= counts.size()) {
            return false;
        }
        return counts.get(position) == null || position < counts.size() - 1;
    }

    /**
     * The driver's refusal of a statement that answers rows in {@code executeUpdate}, quoting the first
     * twenty characters of its text and "..." when it runs on.
     *
     * @param sql the statement text as written
     * @return the refusal
     */
    static SQLException notAnUpdate(final String sql) {
        final String quoted = sql.length() > QUOTED_TEXT_LENGTH ? sql.substring(0, QUOTED_TEXT_LENGTH) + "..." : sql;
        return new SQLException("Statement '" + quoted + "' cannot be executed using current API.", "0A000",
            NOT_AN_UPDATE_CODE);
    }

    /**
     * The driver's answer to {@code executeQuery} over a request of several statements whose first answers an
     * update count. The request has already run by then.
     *
     * @return the refusal
     */
    static SQLException firstResultIsACount() {
        return new SQLException("Query executed successfully, but the first statement returned an update count "
            + "(result set required).", "01000", FIRST_RESULT_COUNT_CODE);
    }
}
