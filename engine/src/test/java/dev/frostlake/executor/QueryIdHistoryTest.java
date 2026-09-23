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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A session's query IDs as LAST_QUERY_ID's index reads them: positive from the first statement, negative
 * back from the last, 0 and past either end naming none — and, when a long session outgrows the kept ends,
 * whatever either end still holds.
 */
public class QueryIdHistoryTest {

    private static QueryIdHistory ran(final int statements, final int firstLimit, final int recentLimit) {
        final QueryIdHistory history = new QueryIdHistory(firstLimit, recentLimit);
        for (int i = 1; i <= statements; i++) {
            history.add("q" + i);
        }
        return history;
    }

    @Test
    public void aShortSessionIsNamedFromEitherEnd() {
        final QueryIdHistory history = ran(5, 1000, 1000);
        assertEquals("q1", history.at(1));
        assertEquals("q5", history.at(5));
        assertEquals("q5", history.at(-1));
        assertEquals("q1", history.at(-5));
        assertNull(history.at(0));
        assertNull(history.at(6));
        assertNull(history.at(-6));
    }

    @Test
    public void anEmptySessionNamesNothing() {
        final QueryIdHistory history = ran(0, 1000, 1000);
        assertNull(history.at(1));
        assertNull(history.at(-1));
    }

    @Test
    public void aLongSessionIsNamedFromWhicheverEndHoldsTheStatement() {
        final QueryIdHistory history = ran(50, 10, 5);
        assertEquals("q50", history.at(-1));
        assertEquals("q46", history.at(-5));
        assertEquals("q46", history.at(46));
        assertEquals("q10", history.at(10));
        assertEquals("q10", history.at(-41));
        // Between the two kept ends: no longer known.
        assertNull(history.at(11));
        assertNull(history.at(-6));
    }
}
