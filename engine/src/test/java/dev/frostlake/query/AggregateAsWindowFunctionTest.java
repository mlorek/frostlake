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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two window-stage gaps found together:
 *
 * <p>(1) Any registered AGGREGATE is usable as a window function over the frame (Snowflake:
 * {@code ARRAY_AGG(x) OVER (PARTITION BY g)}, LISTAGG, MEDIAN, …). Only a hard-coded set (SUM/AVG/MIN/MAX/
 * COUNT) was dispatched; everything else threw "Unsupported window function".
 *
 * <p>(2) A GROUPED query with a window function: after GROUP BY the rows are already SELECT-list-shaped, so
 * the window stage must resolve names against the PROJECTED columns (a window's ORDER BY commonly references
 * an aggregate's alias) and take non-window values positionally. Re-evaluating each item against the BASE
 * table sent aggregate text to the scalar evaluator — "Unknown function: ARRAY_AGG".
 */
public class AggregateAsWindowFunctionTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE t (g INTEGER, v VARCHAR, m INTEGER)");
        engine.execute("INSERT INTO t VALUES (1,'b',10),(1,'a',20),(1,'b',5),(2,'z',7)");
    }

    private ResultSet q(final String sql) {
        return engine.executeQuery(sql);
    }

    // ── aggregates as window functions ───────────────────────────────────────

    @Test
    public void arrayAggOverAPartition() {
        final ResultSet rs = q("SELECT g, ARRAY_AGG(v) OVER (PARTITION BY g) FROM t WHERE g = 2");
        assertEquals("[\"z\"]", String.valueOf(rs.getRows().get(0).getValue(1)));
    }

    @Test
    public void arrayAggDistinctOverAPartition() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "asserts the exact element ORDER of ARRAY_AGG(DISTINCT …) without a WITHIN GROUP clause; the "
            + "order of a distinct aggregation is unspecified, so a real account may build the array in "
            + "any order");
        final ResultSet rs = q("SELECT DISTINCT g, ARRAY_AGG(DISTINCT v) OVER (PARTITION BY g) AS vs "
            + "FROM t ORDER BY g");
        assertEquals("[\"b\",\"a\"]", String.valueOf(rs.getRows().get(0).getValue(1)));
        assertEquals("[\"z\"]", String.valueOf(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void listaggAndMedianOverAPartition() {
        assertEquals("z", String.valueOf(q("SELECT LISTAGG(v) OVER (PARTITION BY g) FROM t WHERE g = 2")
            .getRows().get(0).getValue(0)));
        assertEquals(7.0, ((Number) q("SELECT MEDIAN(m) OVER (PARTITION BY g) FROM t WHERE g = 2")
            .getRows().get(0).getValue(0)).doubleValue());
    }

    // ── window functions over a grouped result ───────────────────────────────

    @Test
    public void aWindowOrderByMayReferenceAnAggregatesAlias() {
        final ResultSet rs = q("SELECT g, SUM(m) AS metric, "
            + "ROW_NUMBER() OVER (PARTITION BY g ORDER BY metric DESC) AS rnk "
            + "FROM t GROUP BY g, v ORDER BY g, rnk");
        assertEquals(3, rs.getRowCount());
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(1)).longValue());   // g=1 best metric first
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(2)).longValue());
        assertEquals(15L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
        assertEquals(2L, ((Number) rs.getRows().get(1).getValue(2)).longValue());
    }

    @Test
    public void aGroupedAggregateItemSurvivesTheWindowStage() {
        // The grouped ARRAY_AGG value must be carried positionally, not re-evaluated per row.
        final ResultSet rs = q("SELECT g, ARRAY_AGG(DISTINCT v) WITHIN GROUP (ORDER BY v) AS vals, "
            + "ROW_NUMBER() OVER (PARTITION BY g ORDER BY g) AS rnk FROM t GROUP BY g ORDER BY g");
        assertEquals("[\"a\",\"b\"]", String.valueOf(rs.getRows().get(0).getValue(1)));
        assertEquals("[\"z\"]", String.valueOf(rs.getRows().get(1).getValue(1)));
    }

    @Test
    public void qualifyFiltersAGroupedWindowRank() {
        final ResultSet rs = q("SELECT g, SUM(m) AS metric, "
            + "ROW_NUMBER() OVER (PARTITION BY g ORDER BY metric DESC) AS rnk "
            + "FROM t GROUP BY g, v QUALIFY rnk = 1 ORDER BY g");
        assertEquals(2, rs.getRowCount());
        assertEquals(20L, ((Number) rs.getRows().get(0).getValue(1)).longValue());
        assertEquals(7L, ((Number) rs.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void plainWindowAndPlainGroupedQueriesAreUnchanged() {
        assertEquals(4, q("SELECT g, ROW_NUMBER() OVER (PARTITION BY g ORDER BY m) FROM t").getRowCount());
        assertEquals(2, q("SELECT g, SUM(m) FROM t GROUP BY g").getRowCount());
    }
}
