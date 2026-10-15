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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The values a statement draws from its counters - a table's IDENTITY, a sequence's NEXTVAL - kept so the
 * write-ahead log can record them and its replay hand the same ones out again. A transaction is logged when it
 * commits, after what other sessions committed while it was open, so a replay drawing afresh would hand the values
 * out in the log's order and two sessions' rows would trade them. A replayed statement takes each value it first
 * drew, in the order it drew them, and a counter it draws past ends where the larger of the two leaves it.
 */
public final class StatementDraws {

    private static final ThreadLocal<List<CounterDraw>> RECORDING = new ThreadLocal<List<CounterDraw>>();
    private static final ThreadLocal<Deque<CounterDraw>> REPLAYING = new ThreadLocal<Deque<CounterDraw>>();

    private StatementDraws() {
    }

    /** Starts recording the calling thread's draws, for a statement the log may keep. */
    public static void beginRecording() {
        RECORDING.set(new ArrayList<CounterDraw>());
    }

    /**
     * Ends recording and answers what was drawn since it began.
     *
     * @return the draws, in order; empty when nothing was recorded
     */
    public static List<CounterDraw> endRecording() {
        final List<CounterDraw> drawn = RECORDING.get();
        RECORDING.remove();
        return drawn == null ? new ArrayList<CounterDraw>() : drawn;
    }

    /**
     * Starts handing out a replayed statement's recorded draws.
     *
     * @param draws what the statement drew when it first ran, in order
     */
    public static void beginReplay(final List<CounterDraw> draws) {
        REPLAYING.set(new ArrayDeque<CounterDraw>(draws));
    }

    /** Ends a replayed statement's draws. */
    public static void endReplay() {
        REPLAYING.remove();
    }

    /**
     * The value a replayed statement first drew from a counter next, or null when it is not replaying, or its next
     * recorded draw is another counter's.
     *
     * @param counter the counter
     * @return the recorded value, taken off the record, or null
     */
    public static Long replayed(final String counter) {
        final Deque<CounterDraw> replay = REPLAYING.get();
        if (replay == null || replay.isEmpty() || !replay.peekFirst().getCounter().equals(counter)) {
            return null;
        }
        return Long.valueOf(replay.pollFirst().getValue());
    }

    /**
     * Notes a value drawn, when the statement drawing it is recorded.
     *
     * @param counter the counter
     * @param value   the value
     */
    public static void record(final String counter, final long value) {
        final List<CounterDraw> recording = RECORDING.get();
        if (recording != null) {
            recording.add(new CounterDraw(counter, value));
        }
    }

    /**
     * The value a counter serves next once a replayed draw handed out {@code drawn}: past whichever of the two is
     * further along its step.
     *
     * @param next  the value the counter was going to serve
     * @param drawn the value the replay handed out
     * @param step  the counter's step
     * @return the value it serves next
     */
    public static long nextAfter(final long next, final long drawn, final long step) {
        final long after = drawn + step;
        return step >= 0 ? Math.max(next, after) : Math.min(next, after);
    }
}
