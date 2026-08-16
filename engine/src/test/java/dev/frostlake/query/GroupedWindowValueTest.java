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
 * What a window computes over GROUPED rows, when its key or its argument is a RAW AGGREGATE the SELECT
 * list never projects. The window stage runs after grouping, so those rows no longer carry what the
 * aggregate needs — SUM(b) has to be computed over the group each output row came from:
 *
 * <pre>
 *   ROW_NUMBER() OVER (ORDER BY SUM(b))   2,1,3    the groups' sums are 30, 5, 101
 *   RANK()       OVER (ORDER BY SUM(b))   2,1,3
 *   SUM(SUM(b))  OVER ()                  136      the aggregate as the window's ARGUMENT
 *   LAG(SUM(b))  OVER (ORDER BY a)        null,30,5
 * </pre>
 *
 * <p>Frostlake used to answer 1,2,3 for the first (input order — the key was the same for every row),
 * 1,1,1 for the second (every row tied) and NULL for the last two. The three groups are deliberately
 * ordered so that the sums do NOT follow the group keys: any ranking that merely echoes input order is
 * visibly wrong.
 */
public class GroupedWindowValueTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT)");
        engine.execute("INSERT INTO gw VALUES (1, 10), (1, 20), (2, 5), (3, 100), (3, 1)");
    }

    /** The LAST column of every row, in the query's own order. */
    private String values(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(",");
            }
            out.append(String.valueOf(rs.getValue(rs.getColumns().size() - 1)));
        }
        return out.toString();
    }

    /** A ranking whose key is a raw aggregate ranks by that aggregate, not by input order. */
    @Test
    public void aRankingOrdersByTheAggregateKey() {
        assertEquals("2,1,3",
            values("SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2,1,3",
            values("SELECT a, RANK() OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2,1,3",
            values("SELECT a, DENSE_RANK() OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("2,3,1",
            values("SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b) DESC) w"
                + " FROM gw GROUP BY a ORDER BY a"));
    }

    /** An aggregate as the window's ARGUMENT is computed over the group too. */
    @Test
    public void anAggregateArgumentIsComputedOverTheGroup() {
        assertEquals("136,136,136",
            values("SELECT a, SUM(SUM(b)) OVER () w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("101,101,101",
            values("SELECT a, MAX(SUM(b)) OVER () w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("5,5,5",
            values("SELECT a, MIN(SUM(b)) OVER () w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("30,35,136",
            values("SELECT a, SUM(SUM(b)) OVER (ORDER BY a) w FROM gw GROUP BY a ORDER BY a"));
    }

    /** The navigation functions read their neighbours' group values. */
    @Test
    public void theNavigationFunctionsReadTheGroupsValues() {
        assertEquals("null,30,5",
            values("SELECT a, LAG(SUM(b)) OVER (ORDER BY a) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("5,101,null",
            values("SELECT a, LEAD(SUM(b)) OVER (ORDER BY a) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("30,30,30",
            values("SELECT a, FIRST_VALUE(SUM(b)) OVER (ORDER BY a) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("101,101,101",
            values("SELECT a, LAST_VALUE(SUM(b)) OVER (ORDER BY a) w FROM gw GROUP BY a ORDER BY a"));
    }

    /**
     * A window that is the ONLY select item ranks every row. Its output rows are identical in value —
     * one column, the rank — which used to collapse them into a single map entry and hand all three the
     * same number. Sorting BY that rank is what makes the assertion deterministic: three distinct ranks
     * sort to 1,2,3, where the collapse gave 1,1,1 whatever the ordering.
     */
    @Test
    public void aLoneWindowItemStillRanksEveryRow() {
        assertEquals("1,2,3", values("SELECT ROW_NUMBER() OVER (ORDER BY a) w FROM gw GROUP BY a"));
        assertEquals("1,2,3",
            values("SELECT ROW_NUMBER() OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a ORDER BY 1"));
        assertEquals("1,2,3",
            values("SELECT RANK() OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a ORDER BY 1"));
    }

    /** HAVING narrows the groups first, and the ranking runs over what survives. */
    @Test
    public void theRankingRunsOverWhatHavingLeaves() {
        assertEquals("1,2",
            values("SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a"
                + " HAVING SUM(b) > 5 ORDER BY a"));
    }

    /** The shapes that already worked are untouched — a group key, a star count, a partitioned rank. */
    @Test
    public void theWorkingShapesAreUntouched() {
        assertEquals("1,2,3",
            values("SELECT a, ROW_NUMBER() OVER (ORDER BY a) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("3,3,3",
            values("SELECT a, COUNT(*) OVER () w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("1,1,2",
            values("SELECT a, NTILE(2) OVER (ORDER BY SUM(b)) w FROM gw GROUP BY a ORDER BY a"));
        assertEquals("1,1,1",
            values("SELECT a, ROW_NUMBER() OVER (PARTITION BY a ORDER BY SUM(b)) w"
                + " FROM gw GROUP BY a ORDER BY a"));
        assertEquals("30,5,101", values("SELECT a, SUM(b) w FROM gw GROUP BY a ORDER BY a"));
    }
}
