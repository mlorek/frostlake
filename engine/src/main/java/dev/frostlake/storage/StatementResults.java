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


package dev.frostlake.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The results a statement reads by RESULT_SCAN, kept so the write-ahead log can record them and its replay read them
 * again. The replay runs a statement after whatever its log position puts before it and in a session whose queries
 * are the replay's own, so neither the result a query ID names nor the query {@code LAST_QUERY_ID(-2)} names is
 * there to read: a replayed statement reads, for each query ID it asks for, the result it first read for the query ID
 * it asked for in that place, in the order it first asked.
 */
public final class StatementResults {

    /** The key a missing query ID is kept under: no query has an empty ID. */
    private static final String NO_ID = "";

    private static final ThreadLocal<Map<String, ResultSet>> RECORDING = new ThreadLocal<Map<String, ResultSet>>();
    private static final ThreadLocal<LinkedList<RecordedResult>> REPLAYING =
        new ThreadLocal<LinkedList<RecordedResult>>();
    private static final ThreadLocal<Map<String, ResultSet>> REPLAYED = new ThreadLocal<Map<String, ResultSet>>();

    private StatementResults() {
    }

    /** Starts recording the results the calling thread's statement reads, for a statement the log may keep. */
    public static void beginRecording() {
        RECORDING.set(new LinkedHashMap<String, ResultSet>());
    }

    /**
     * Ends recording and answers what was read since it began.
     *
     * @param keep whether the statement is logged, so its results are copied for the log; when not, nothing is
     * @return each result read, in the order its query ID was first asked for; empty when nothing was recorded or
     *         kept
     */
    public static List<RecordedResult> endRecording(final boolean keep) {
        final Map<String, ResultSet> read = RECORDING.get();
        RECORDING.remove();
        final List<RecordedResult> recorded = new ArrayList<RecordedResult>();
        if (read != null && keep) {
            for (final ResultSet result : read.values()) {
                recorded.add(RecordedResult.of(result));
            }
        }
        return recorded;
    }

    /**
     * Whether the calling thread records what its statement reads, for a statement the log may keep.
     *
     * @return true while recording
     */
    public static boolean isRecording() {
        return RECORDING.get() != null;
    }

    /**
     * Records what a statement inside the recorded one reads apart from it, until {@link #endApart}: the results of a
     * statement the log may keep in a record of its own.
     *
     * @return what was being recorded, for {@link #endApart}; null when nothing was
     */
    public static Map<String, ResultSet> beginApart() {
        final Map<String, ResultSet> displaced = RECORDING.get();
        if (displaced != null) {
            RECORDING.set(new LinkedHashMap<String, ResultSet>());
        }
        return displaced;
    }

    /**
     * Ends recording apart and goes back to what {@link #beginApart} displaced.
     *
     * @param displaced what {@link #beginApart} answered
     * @return the results read apart, by what they were asked for, in order; empty when nothing was being recorded
     */
    public static Map<String, ResultSet> endApart(final Map<String, ResultSet> displaced) {
        final Map<String, ResultSet> apart = RECORDING.get();
        if (displaced == null) {
            return new LinkedHashMap<String, ResultSet>();
        }
        RECORDING.set(displaced);
        return apart == null ? new LinkedHashMap<String, ResultSet>() : apart;
    }

    /**
     * Gives results read apart back to the statement they were set apart from, as if never set apart: they went with
     * no record of their own.
     *
     * @param displaced what {@link #beginApart} answered
     * @param apart     what {@link #endApart} answered
     */
    public static void rejoin(final Map<String, ResultSet> displaced, final Map<String, ResultSet> apart) {
        if (displaced == null) {
            return;
        }
        for (final Map.Entry<String, ResultSet> read : apart.entrySet()) {
            if (!displaced.containsKey(read.getKey())) {
                displaced.put(read.getKey(), read.getValue());
            }
        }
    }

    /**
     * Results read apart as a record of their own keeps them.
     *
     * @param apart what {@link #endApart} answered
     * @return each result, in the order it was first asked for
     */
    public static List<RecordedResult> recorded(final Map<String, ResultSet> apart) {
        final List<RecordedResult> recorded = new ArrayList<RecordedResult>();
        for (final ResultSet result : apart.values()) {
            recorded.add(RecordedResult.of(result));
        }
        return recorded;
    }

    /**
     * Notes a result read, the first time its query ID is asked for, when the statement reading it is recorded.
     *
     * @param queryId the query ID asked for, or null
     * @param result  the result it named
     */
    public static void record(final String queryId, final ResultSet result) {
        final Map<String, ResultSet> recording = RECORDING.get();
        final String key = queryId == null ? NO_ID : queryId;
        if (recording != null && !recording.containsKey(key)) {
            recording.put(key, result);
        }
    }

    /**
     * Starts handing a replayed statement the results it first read.
     *
     * @param results what the statement read when it first ran, in order; a null stands for one the log could not
     *                keep, which the replay reads as the query ID names it now
     */
    public static void beginReplay(final List<RecordedResult> results) {
        REPLAYING.set(new LinkedList<RecordedResult>(results));
        REPLAYED.set(new HashMap<String, ResultSet>());
    }

    /** Ends a replayed statement's results. */
    public static void endReplay() {
        REPLAYING.remove();
        REPLAYED.remove();
    }

    /**
     * The result a replayed statement first read for a query ID asked for in this place: the next recorded one the
     * first time the ID is asked for, the same one after. Null when the statement is not replaying, read nothing
     * more, or the log could not keep that result.
     *
     * @param queryId the query ID the replay asks for, or null
     * @return the recorded result, or null
     */
    public static ResultSet replayed(final String queryId) {
        final LinkedList<RecordedResult> replay = REPLAYING.get();
        if (replay == null) {
            return null;
        }
        final String key = queryId == null ? NO_ID : queryId;
        final Map<String, ResultSet> handed = REPLAYED.get();
        if (handed.containsKey(key)) {
            return handed.get(key);
        }
        if (replay.isEmpty()) {
            return null;
        }
        final RecordedResult next = replay.removeFirst();
        final ResultSet result = next == null ? null : next.toResultSet();
        handed.put(key, result);
        return result;
    }
}
