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

import dev.frostlake.executor.operators.MemoizedRows;
import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.storage.Row;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The tables a statement reads only as they were at an AT or BEFORE point, whose statistics are that version's: the
 * row count bounding a COUNT and each column's least and greatest value, which the storage tags SYSTEM$TYPEOF prints
 * are drawn from. Over a table that has shrunk since, a COUNT of its 300 earlier rows is tagged {@code [SB2]} as the
 * same rows copied into a table are, never {@code [SB1]} by the 5 it holds now (live-verified). A table the statement
 * also reads as it is now, or at two points, keeps its current statistics.
 *
 * <p>Pinned for the outermost statement in a ThreadLocal, as the statement clock is: the statistics are asked for
 * deep in the planning, where only a table's name is at hand.
 */
final class TimeTravelReads {

    /** A table read as it is now, or at more than one point. */
    private static final Object CURRENT = new Object();

    private static final ThreadLocal<Map<String, Object>> READS = new ThreadLocal<Map<String, Object>>();

    private TimeTravelReads() {
    }

    /**
     * Start a statement's record, answering the one it displaces.
     *
     * @return the record before
     */
    static Map<String, Object> begin() {
        final Map<String, Object> previous = READS.get();
        READS.set(new HashMap<String, Object>());
        return previous;
    }

    /** Put back what {@link #begin} displaced. */
    static void end(final Map<String, Object> previous) {
        if (previous == null) {
            READS.remove();
        } else {
            READS.set(previous);
        }
    }

    /**
     * Record a read of a table at a point.
     *
     * @param qualifiedName the table's stored name
     * @param rows          the rows of that version
     */
    static void readAsOf(final String qualifiedName, final RowsProvider rows) {
        final Map<String, Object> reads = READS.get();
        if (reads != null && qualifiedName != null) {
            reads.put(qualifiedName, reads.containsKey(qualifiedName) ? CURRENT : new MemoizedRows(rows));
        }
    }

    /**
     * Record a read of a table as it is now.
     *
     * @param qualifiedName the table's stored name
     */
    static void readCurrent(final String qualifiedName) {
        final Map<String, Object> reads = READS.get();
        if (reads != null && qualifiedName != null) {
            reads.put(qualifiedName, CURRENT);
        }
    }

    /**
     * The rows a table's statistics are drawn from, when the statement reads it at one point alone.
     *
     * @param qualifiedName the table's stored name
     * @return that version's rows, or null when the table's current rows are its statistics
     */
    static List<Row> statisticsRows(final String qualifiedName) {
        final Map<String, Object> reads = READS.get();
        final Object read = reads == null || qualifiedName == null ? null : reads.get(qualifiedName);
        return read instanceof MemoizedRows ? ((MemoizedRows) read).rows() : null;
    }
}
