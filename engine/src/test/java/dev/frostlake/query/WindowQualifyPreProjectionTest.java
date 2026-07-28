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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * QUALIFY evaluates over the PRE-projection rows, as in Snowflake: its predicate and its inline windows
 * may reference FROM columns that are not in the SELECT list, or partition by expressions over them.
 * When the SELECT list itself contains a window function the engine reshapes rows into SELECT-list form
 * for the window stage — QUALIFY must run BEFORE that reshape. Previously the reshaped rows were handed
 * to the base-table resolver, a partition key like {@code GET(SPLIT(fname,'/'),3)} silently read the
 * wrong slot, every row landed in one NULL partition, and {@code = 1} kept a single arbitrary row (this
 * dropped versioned staging rows in a real loader).
 */
public class WindowQualifyPreProjectionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE feed_files (doc VARCHAR, fname VARCHAR)");
        engine.execute(
            """
            INSERT INTO feed_files VALUES
                ('d1', 'a/b/x-20230629.gz'),
                ('d1', 'a/b/x-20230630.gz'),
                ('d1', 'a/b/x-20230705.gz')
            """);
    }

    @Test
    public void qualifyWindowPartitionedByBaseExpressionKeepsAllPartitions() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT ROW_NUMBER() OVER (PARTITION BY doc ORDER BY fname DESC) AS rnk, fname
            FROM feed_files s
            QUALIFY ROW_NUMBER() OVER (PARTITION BY GET(SPLIT(s.fname, '/'), 2) ORDER BY s.fname DESC) = 1
            ORDER BY fname
            """);
        assertEquals(3, result.getRows().size());
        assertEquals(3L, ((Number) result.getRows().get(0).getValue(0)).longValue());
        assertEquals(1L, ((Number) result.getRows().get(2).getValue(0)).longValue());
    }

    @Test
    public void qualifyWindowPartitionedBySelectAliasOfDeepExpression() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT REPLACE(GET(SPLIT(s.fname, '/'), 2), '.gz') AS piece,
                   ROW_NUMBER() OVER (PARTITION BY doc ORDER BY piece DESC) AS rnk
            FROM feed_files s
            QUALIFY ROW_NUMBER() OVER (PARTITION BY doc, piece ORDER BY s.fname DESC) = 1
            ORDER BY piece
            """);
        assertEquals(3, result.getRows().size());
    }

    @Test
    public void qualifyOnSelectWindowAliasStillFilters() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT fname, ROW_NUMBER() OVER (PARTITION BY doc ORDER BY fname DESC) AS rnk
            FROM feed_files s
            QUALIFY rnk = 1
            """);
        assertEquals(1, result.getRows().size());
        assertEquals("a/b/x-20230705.gz", result.getRows().get(0).getValue(0));
    }

    @Test
    public void qualifyReferencingNonWindowAliasOverBaseRows() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT UPPER(fname) AS shout, ROW_NUMBER() OVER (ORDER BY fname) AS rnk
            FROM feed_files s
            QUALIFY shout LIKE '%20230630%'
            """);
        assertEquals(1, result.getRows().size());
        assertEquals("A/B/X-20230630.GZ", result.getRows().get(0).getValue(0));
    }

    @Test
    public void qualifyPredicateOverBaseColumnNotInSelectList() {
        final ResultSet result = engine.executeQuery(
            """
            SELECT ROW_NUMBER() OVER (ORDER BY fname) AS rnk
            FROM feed_files s
            QUALIFY s.fname != 'a/b/x-20230630.gz'
            ORDER BY rnk
            """);
        assertEquals(2, result.getRows().size());
    }

    @Test
    public void groupedWindowWithQualifyKeepsProjectedSemantics() {
        engine.execute("CREATE TABLE sales (region VARCHAR, amt NUMBER)");
        engine.execute("INSERT INTO sales VALUES ('e', 10), ('e', 20), ('w', 5)");
        final ResultSet result = engine.executeQuery(
            """
            SELECT region, SUM(amt) AS total, RANK() OVER (ORDER BY total DESC) AS rk
            FROM sales GROUP BY region
            QUALIFY rk = 1
            """);
        assertEquals(1, result.getRows().size());
        assertEquals("e", result.getRows().get(0).getValue(0));
    }
}
