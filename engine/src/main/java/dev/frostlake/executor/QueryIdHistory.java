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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/**
 * One session's query IDs in the order its statements ran, as LAST_QUERY_ID reads them (live-verified): a
 * positive index counts from the session's FIRST statement (1 is the first), a negative one back from the
 * most recent (-1 is the last), and 0, or an index past either end of the history, names no statement.
 *
 * <p>Both ends are kept, bounded: the first statements' IDs and the most recent ones, plus how many
 * statements ran. An index that falls between the two kept ends of a very long session names no statement
 * here, where the account would still name one.
 */
final class QueryIdHistory {

    private final int firstLimit;
    private final int recentLimit;
    private final List<String> first = new ArrayList<>();
    private final Deque<String> recent = new ArrayDeque<>();
    private long count;

    QueryIdHistory(final int firstLimit, final int recentLimit) {
        this.firstLimit = firstLimit;
        this.recentLimit = recentLimit;
    }

    /** Records the ID of the statement that just ran. */
    void add(final String queryId) {
        count++;
        if (first.size() < firstLimit) {
            first.add(queryId);
        }
        recent.addFirst(queryId);
        while (recent.size() > recentLimit) {
            recent.removeLast();
        }
    }

    /**
     * The ID a LAST_QUERY_ID index names, or null when it names no statement.
     *
     * @param index a 1-based ordinal from the first statement when positive, from the last when negative
     * @return the query ID, or null
     */
    String at(final long index) {
        if (index == 0 || Math.abs(index) > count) {
            return null;
        }
        // The same statement both ways: its ordinal from the first, and how far back it is from the last.
        final long ordinal = index > 0 ? index : count + index + 1;
        final long back = count - ordinal;
        if (back < recent.size()) {
            final Iterator<String> newestFirst = recent.iterator();
            for (long skipped = 0; skipped < back; skipped++) {
                newestFirst.next();
            }
            return newestFirst.next();
        }
        return ordinal <= first.size() ? first.get((int) (ordinal - 1)) : null;
    }
}
