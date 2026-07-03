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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CONDITIONAL_TRUE_EVENT(expr) / CONDITIONAL_CHANGE_EVENT(expr) ordered window functions. Both start at
 * 0 per partition. TRUE_EVENT increments on each row (up to and including the current) where expr is
 * TRUE; CHANGE_EVENT increments each time expr's value differs from the previous row's, treating any
 * NULL-involved step as "not a change". The o_col column {0,0,13,13,14,15,NULL} reproduces the exact
 * sequences from the Snowflake docs: TRUE_EVENT(o_col) → 0,0,1,2,3,4,4 and CHANGE_EVENT → 0,0,1,1,2,3,3.
 */
public class WindowConditionalEventTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE events (seq INTEGER, o_col INTEGER)");
        engine.execute("INSERT INTO events VALUES (1,0),(2,0),(3,13),(4,13),(5,14),(6,15)");
        engine.execute("INSERT INTO events VALUES (7, NULL)");
    }

    /** Runs {@code <windowExpr> OVER (ORDER BY seq)} and asserts each row's value, keyed by seq. */
    private void assertSequence(final String windowExpr, final long[] expectedBySeq) {
        final ResultSet rs = engine.executeQuery(
            "SELECT seq, " + windowExpr + " OVER (ORDER BY seq) AS w FROM events");
        assertEquals(expectedBySeq.length, rs.getRowCount());
        for (final Row r : rs.getRows()) {
            final int seq = (int) ((Number) r.getValue(0)).longValue();
            final long w = ((Number) r.getValue(1)).longValue();
            assertEquals(expectedBySeq[seq - 1], w, "seq " + seq);
        }
    }

    @Test
    public void trueEventOnBareValue() {
        // o_col as a boolean: 0 is falsy, non-zero truthy, NULL not truthy.
        assertSequence("CONDITIONAL_TRUE_EVENT(o_col)", new long[] {0, 0, 1, 2, 3, 4, 4});
    }

    @Test
    public void trueEventOnPredicate() {
        assertSequence("CONDITIONAL_TRUE_EVENT(o_col > 12)", new long[] {0, 0, 1, 2, 3, 4, 4});
    }

    @Test
    public void changeEventCountsDistinctRuns() {
        // Increments at each value change; the trailing NULL is not a change and carries the count.
        assertSequence("CONDITIONAL_CHANGE_EVENT(o_col)", new long[] {0, 0, 1, 1, 2, 3, 3});
    }

    @Test
    public void partitionByResetsTheCount() {
        engine.execute("CREATE TABLE readings (province VARCHAR, seq INTEGER, v INTEGER)");
        engine.execute("INSERT INTO readings VALUES "
            + "('AB',1,120),('AB',2,120),('AB',3,0),('MB',4,30),('MB',5,30),('MB',6,99)");
        final ResultSet rs = engine.executeQuery(
            "SELECT seq, CONDITIONAL_CHANGE_EVENT(v) OVER (PARTITION BY province ORDER BY seq) AS w "
            + "FROM readings");
        for (final Row r : rs.getRows()) {
            final int seq = (int) ((Number) r.getValue(0)).longValue();
            final long w = ((Number) r.getValue(1)).longValue();
            // AB: 120,120,0 → 0,0,1 ; MB restarts at 0 despite v differing across the boundary: 30,30,99 → 0,0,1.
            final long expected = (seq == 3 || seq == 6) ? 1L : 0L;
            assertEquals(expected, w, "seq " + seq);
        }
    }
}
