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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Sliding-frame window aggregates (ROWS BETWEEN ...), which run through the generic
 * per-frame accumulator path shared by SUM / AVG / COUNT / MIN / MAX.
 */
public class WindowFrameAggregateTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(WindowFrameAggregateTest.class);

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE wf (g VARCHAR, i INTEGER, x INTEGER)");
        engine.execute("INSERT INTO wf VALUES ('a',1,10),('a',2,20),('a',3,30),('b',1,5),('b',2,7)");
    }

    private long longAt(final ResultSet rs, final int row, final int col) {
        return ((Number) rs.getRows().get(row).getValue(col)).longValue();
    }

    @Test
    public void sumOverSlidingRowsFrame() {
        final ResultSet rs = engine.executeQuery("""
            SELECT g, i, SUM(x) OVER (PARTITION BY g ORDER BY i ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS s
            FROM wf ORDER BY g, i
            """);
        assertEquals(5, rs.getRows().size());
        assertEquals(10, longAt(rs, 0, 2));
        assertEquals(30, longAt(rs, 1, 2));
        assertEquals(50, longAt(rs, 2, 2));
        assertEquals(5, longAt(rs, 3, 2));
        assertEquals(12, longAt(rs, 4, 2));
    }

    @Test
    public void avgAndCountOverSlidingRowsFrame() {
        final ResultSet rs = engine.executeQuery("""
            SELECT g, i,
                   AVG(x)   OVER (PARTITION BY g ORDER BY i ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS a,
                   COUNT(x) OVER (PARTITION BY g ORDER BY i ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS c
            FROM wf ORDER BY g, i
            """);
        assertEquals(10.0, ((Number) rs.getRows().get(0).getValue(2)).doubleValue());
        assertEquals(15.0, ((Number) rs.getRows().get(1).getValue(2)).doubleValue());
        assertEquals(25.0, ((Number) rs.getRows().get(2).getValue(2)).doubleValue());
        assertEquals(6.0, ((Number) rs.getRows().get(4).getValue(2)).doubleValue());
        assertEquals(1, longAt(rs, 0, 3));
        assertEquals(2, longAt(rs, 1, 3));
        assertEquals(2, longAt(rs, 2, 3));
        assertEquals(2, longAt(rs, 4, 3));
    }

    @Test
    public void minMaxOverSlidingRowsFrame() {
        final ResultSet rs = engine.executeQuery("""
            SELECT g, i,
                   MIN(x) OVER (PARTITION BY g ORDER BY i ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS mn,
                   MAX(x) OVER (PARTITION BY g ORDER BY i ROWS BETWEEN 1 PRECEDING AND CURRENT ROW) AS mx
            FROM wf ORDER BY g, i
            """);
        assertEquals(10, longAt(rs, 0, 2));
        assertEquals(10, longAt(rs, 1, 2));
        assertEquals(20, longAt(rs, 2, 2));
        assertEquals(10, longAt(rs, 0, 3));
        assertEquals(20, longAt(rs, 1, 3));
        assertEquals(30, longAt(rs, 2, 3));
    }

    @Test
    public void runningTotalUnboundedPrecedingFrame() {
        final ResultSet rs = engine.executeQuery("""
            SELECT i, SUM(x) OVER (ORDER BY g, i ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS s
            FROM wf ORDER BY g, i
            """);
        assertEquals(10, longAt(rs, 0, 1));
        assertEquals(30, longAt(rs, 1, 1));
        assertEquals(60, longAt(rs, 2, 1));
        assertEquals(65, longAt(rs, 3, 1));
        assertEquals(72, longAt(rs, 4, 1));
        logger.info("Running totals verified over the accumulator frame path");
    }
}
