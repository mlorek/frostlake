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

package dev.frostlake.perf;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Micro-bench for the window-frame and grouped-aggregate paths (the targeted-vector work's
 * baseline). MEASURES and logs (grep the log for "PERF |"); assertions only check row counts and
 * spot values — never timings. Run on demand: {@code mvn verify -Pperf -Dtest=WindowAggregatePerformanceTest}.
 *
 * <p>The shapes exercised, chosen to isolate the per-output-row frame costs:
 * <ul>
 *   <li>running frame — {@code SUM(x) OVER (ORDER BY id)}: the default frame re-walks a growing
 *       prefix per output row, so argument evaluation is the quadratic term;</li>
 *   <li>sliding frame — {@code ROWS BETWEEN 100 PRECEDING AND CURRENT ROW};</li>
 *   <li>event scan — {@code CONDITIONAL_TRUE_EVENT} (a prefix walk per output row);</li>
 *   <li>grouped — {@code SUM/AVG/COUNT ... GROUP BY} over a plain column (the column-vector path);</li>
 *   <li>window value function — {@code LAST_VALUE(x) OVER (... )} (whole-partition value list).</li>
 * </ul>
 */
@Tag("perf")
public class WindowAggregatePerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(WindowAggregatePerformanceTest.class);
    private static final int WINDOW_ROWS = 4000;
    private static final int GROUPED_ROWS = 100000;
    private static DatabaseEngine engine;

    @BeforeAll
    public static void setup() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE wperf_db");
        engine.execute("USE DATABASE wperf_db");
        engine.execute("CREATE SCHEMA perf");
        engine.execute("USE SCHEMA perf");
        engine.execute("CREATE TABLE w (id INTEGER, grp VARCHAR, x INTEGER)");
        loadRows("w", WINDOW_ROWS);
        engine.execute("CREATE TABLE g (id INTEGER, grp VARCHAR, x INTEGER)");
        loadRows("g", GROUPED_ROWS);
    }

    @AfterAll
    public static void teardown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private static void loadRows(final String table, final int rows) {
        final StringBuilder values = new StringBuilder();
        int inBatch = 0;
        for (int i = 0; i < rows; i++) {
            if (inBatch > 0) {
                values.append(',');
            }
            values.append('(').append(i).append(",'g").append(i % 20).append("',").append(i % 1000).append(')');
            inBatch++;
            if (inBatch == 5000 || i == rows - 1) {
                engine.execute("INSERT INTO " + table + " (id, grp, x) VALUES " + values);
                values.setLength(0);
                inBatch = 0;
            }
        }
    }

    /** Time one query, logging PERF | label | millis, and return its result for spot checks. */
    private ResultSet timed(final String label, final String sql) {
        final long start = System.nanoTime();
        final ResultSet rs = engine.executeQuery(sql);
        final long ms = (System.nanoTime() - start) / 1_000_000;
        log.info("PERF | {} | {} ms | {} rows", label, ms, rs.getRows().size());
        return rs;
    }

    @Test
    public void runningFrameSum() {
        timed("window.runningSum.warm", "SELECT id, SUM(x) OVER (ORDER BY id) FROM w");
        final ResultSet rs = timed("window.runningSum", "SELECT id, SUM(x) OVER (ORDER BY id) FROM w");
        assertEquals(WINDOW_ROWS, rs.getRows().size());
    }

    @Test
    public void runningFramePartitionedAvg() {
        final ResultSet rs = timed("window.partitionedRunningAvg",
            "SELECT id, AVG(x) OVER (PARTITION BY grp ORDER BY id) FROM w");
        assertEquals(WINDOW_ROWS, rs.getRows().size());
    }

    @Test
    public void slidingFrameSum() {
        final ResultSet rs = timed("window.slidingSum",
            "SELECT id, SUM(x) OVER (ORDER BY id ROWS BETWEEN 100 PRECEDING AND CURRENT ROW) FROM w");
        assertEquals(WINDOW_ROWS, rs.getRows().size());
    }

    @Test
    public void conditionalTrueEvent() {
        final ResultSet rs = timed("window.conditionalTrueEvent",
            "SELECT id, CONDITIONAL_TRUE_EVENT(x > 500) OVER (ORDER BY id) FROM w");
        assertEquals(WINDOW_ROWS, rs.getRows().size());
    }

    @Test
    public void lastValueWholePartition() {
        final ResultSet rs = timed("window.lastValue",
            "SELECT id, LAST_VALUE(x) OVER (PARTITION BY grp ORDER BY id) FROM w");
        assertEquals(WINDOW_ROWS, rs.getRows().size());
    }

    @Test
    public void groupedPlainColumnAggregates() {
        timed("grouped.sumAvgCount.warm",
            "SELECT grp, SUM(x), AVG(x), COUNT(x) FROM g GROUP BY grp");
        final ResultSet rs = timed("grouped.sumAvgCount",
            "SELECT grp, SUM(x), AVG(x), COUNT(x) FROM g GROUP BY grp");
        assertEquals(20, rs.getRows().size());
    }

    @Test
    public void groupedMinMax() {
        final ResultSet rs = timed("grouped.minMax",
            "SELECT grp, MIN(x), MAX(x) FROM g GROUP BY grp");
        assertEquals(20, rs.getRows().size());
    }
}
