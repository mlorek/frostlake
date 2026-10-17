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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a statement the write-ahead log keeps read of its own identity: the session it ran in (CURRENT_SESSION), its
 * transaction (CURRENT_TRANSACTION) and the query IDs before it (LAST_QUERY_ID). A replay runs in a session of its
 * own, with transactions and query IDs of its own, so it would store other values than the statement stored when it
 * first ran; the log keeps what each of them answered, and the replay answers that again.
 *
 * <p>Each identity is kept as the values it answered, each once, in the order the statement first met them, because
 * it answers one value for many rows and for every statement of a block until the identity moves on. The replay
 * meets its own values in the same order and puts the first one recorded in place of the first one it meets, the
 * second in place of the second, and so on, however many times it asks.
 */
public final class StatementAnswers {

    private static final ThreadLocal<Map<String, List<String>>> RECORDING =
        new ThreadLocal<Map<String, List<String>>>();
    private static final ThreadLocal<Map<String, List<String>>> REPLAYING =
        new ThreadLocal<Map<String, List<String>>>();
    private static final ThreadLocal<Map<String, Map<String, String>>> REPLACED =
        new ThreadLocal<Map<String, Map<String, String>>>();

    /** Stands for a null answer as a key of the replay's map: no answer has this text. */
    private static final String NULL_ANSWER = "\u0000null";

    private StatementAnswers() {
    }

    /** Starts recording the calling thread's answers, for a statement the log may keep. */
    public static void beginRecording() {
        RECORDING.set(new LinkedHashMap<String, List<String>>());
    }

    /**
     * Ends recording and answers what was recorded since it began.
     *
     * @return each identity's values, by identity, in the order they were first met; empty when nothing was recorded
     */
    public static Map<String, List<String>> endRecording() {
        final Map<String, List<String>> recorded = RECORDING.get();
        RECORDING.remove();
        return recorded == null ? new LinkedHashMap<String, List<String>>() : recorded;
    }

    /**
     * Records what a statement inside the recorded one answers apart from it, until {@link #endApart}: the answers
     * of a statement the log may keep in a record of its own.
     *
     * @return what was being recorded, for {@link #endApart}; null when nothing was
     */
    public static Map<String, List<String>> beginApart() {
        final Map<String, List<String>> displaced = RECORDING.get();
        if (displaced != null) {
            RECORDING.set(new LinkedHashMap<String, List<String>>());
        }
        return displaced;
    }

    /**
     * Ends recording apart and goes back to what {@link #beginApart} displaced.
     *
     * @param displaced what {@link #beginApart} answered
     * @return the answers recorded apart; empty when nothing was being recorded
     */
    public static Map<String, List<String>> endApart(final Map<String, List<String>> displaced) {
        final Map<String, List<String>> apart = RECORDING.get();
        if (displaced == null) {
            return new LinkedHashMap<String, List<String>>();
        }
        RECORDING.set(displaced);
        return apart == null ? new LinkedHashMap<String, List<String>>() : apart;
    }

    /**
     * Gives answers recorded apart back to the statement they were set apart from, as if never set apart: they went
     * with no record of their own.
     *
     * @param displaced what {@link #beginApart} answered
     * @param apart     what {@link #endApart} answered
     */
    public static void rejoin(final Map<String, List<String>> displaced, final Map<String, List<String>> apart) {
        if (displaced == null) {
            return;
        }
        for (final Map.Entry<String, List<String>> identity : apart.entrySet()) {
            for (final String value : identity.getValue()) {
                recordValue(displaced, identity.getKey(), value);
            }
        }
    }

    /**
     * Starts answering a replayed statement's recorded values.
     *
     * @param answers what the statement's identities answered when it first ran
     */
    public static void beginReplay(final Map<String, List<String>> answers) {
        REPLAYING.set(new HashMap<String, List<String>>(answers));
        REPLACED.set(new HashMap<String, Map<String, String>>());
    }

    /** Ends a replayed statement's answers. */
    public static void endReplay() {
        REPLAYING.remove();
        REPLACED.remove();
    }

    /**
     * What an identity answers: the value it answered when the statement first ran, while the statement replays;
     * otherwise the value it has, which a recorded statement notes.
     *
     * @param identity the identity, CURRENT_SESSION, CURRENT_TRANSACTION or LAST_QUERY_ID(n)
     * @param value    the value it has now, or null
     * @return the value to answer, or null
     */
    public static String answered(final String identity, final String value) {
        final Map<String, List<String>> replay = REPLAYING.get();
        if (replay != null) {
            final Map<String, String> replaced = replacedOf(identity);
            final String key = value == null ? NULL_ANSWER : value;
            if (replaced.containsKey(key)) {
                return replaced.get(key);
            }
            final List<String> recorded = replay.get(identity);
            final String answer = recorded != null && replaced.size() < recorded.size()
                ? recorded.get(replaced.size()) : value;
            replaced.put(key, answer);
            return answer;
        }
        final Map<String, List<String>> recording = RECORDING.get();
        if (recording != null) {
            recordValue(recording, identity, value);
        }
        return value;
    }

    /** The replay's map from the values one identity meets now to the ones it answered when first run. */
    private static Map<String, String> replacedOf(final String identity) {
        final Map<String, Map<String, String>> replaced = REPLACED.get();
        Map<String, String> ofIdentity = replaced.get(identity);
        if (ofIdentity == null) {
            ofIdentity = new HashMap<String, String>();
            replaced.put(identity, ofIdentity);
        }
        return ofIdentity;
    }

    /** Notes a value an identity answered, unless it answered it before. */
    private static void recordValue(final Map<String, List<String>> into, final String identity, final String value) {
        List<String> values = into.get(identity);
        if (values == null) {
            values = new ArrayList<String>();
            into.put(identity, values);
        }
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
