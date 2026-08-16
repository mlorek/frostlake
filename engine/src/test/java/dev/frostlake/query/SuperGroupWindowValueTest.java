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
 * The VALUE a window computes over ROLLUP / CUBE / GROUPING SETS rows. It used to ignore its own
 * ORDER BY and number the rows in the order the super-group produced them:
 *
 * <pre>
 *   SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) FROM gw GROUP BY ROLLUP(a) ORDER BY a
 *       was  1/1, 2/2, 3/3, 4/4, null/5     — group order, so the window's ORDER BY did nothing
 *       live 1/3, 2/1, 3/4, 4/2, null/5
 * </pre>
 *
 * <p>ONE CAUSE, TWO SYMPTOMS. This is the same missing group-rows sink that stopped a super-group
 * resolving an unprojected ORDER BY key: a window's raw aggregate is computed over each row's SOURCE
 * GROUP, and with no source group there was nothing to order by. Passing the sink through fixed both,
 * so nothing here needed its own change — the file exists to PIN the values, because the two defects
 * looked unrelated from the outside and a regression in one would be read as the other.
 *
 * <p>The ORDER BY in these queries is incidental: {@code a} is selected, so it resolves whatever the
 * window does. The assertions are about the second column.
 */
public class SuperGroupWindowValueTest extends BaseDatabaseTest {

    /**
     * EVERY k HOLDS TWO GROUPS, which is not decoration. A partition with a single detail group has a
     * SUBTOTAL equal to that group by arithmetic, so a window ordered by the aggregate ties the two —
     * an undetermined order that Frostlake resolved one way every run and live resolved either.
     */
    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE gw (a INT, b INT, k VARCHAR)");
        engine.execute(
            "INSERT INTO gw VALUES (1, 30, 'x'), (2, 10, 'x'), (3, 50, 'y'), (4, 20, 'y')");
    }

    /** Every row's every column, joined. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        while (rs.next()) {
            if (all.length() > 0) {
                all.append(",");
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    all.append("/");
                }
                all.append(String.valueOf(rs.getValue(c)));
            }
        }
        return all.toString();
    }

    /** The rankings order by their own key — the groups' sums are 30, 10, 50, 20 and the total 110. */
    @Test
    public void theRankingsOrderByTheirOwnKey() {
        assertEquals("1/3,2/1,3/4,4/2,null/5", answer(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("1/3,2/1,3/4,4/2,null/5", answer(
            "SELECT a, RANK() OVER (ORDER BY SUM(b)) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("1/3,2/1,3/4,4/2,null/5", answer(
            "SELECT a, DENSE_RANK() OVER (ORDER BY SUM(b)) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("1/3,2/5,3/2,4/4,null/1", answer(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b) DESC) FROM gw"
                + " GROUP BY ROLLUP(a) ORDER BY a"),
            "DESC reverses it, and the grand total leads because its sum is the largest");
    }

    /** CUBE and GROUPING SETS carry the same values as ROLLUP over one dimension. */
    @Test
    public void everySuperGroupSpellingAgrees() {
        assertEquals("1/3,2/1,3/4,4/2,null/5", answer(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) FROM gw GROUP BY CUBE(a) ORDER BY a"));
        assertEquals("1/3,2/1,3/4,4/2", answer(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) FROM gw"
                + " GROUP BY GROUPING SETS ((a)) ORDER BY a"));
        assertEquals("1/3,2/1,3/4,4/2", answer(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY SUM(b)) FROM gw GROUP BY a ORDER BY a"),
            "and a plain GROUP BY, which was fixed first and must not move");
    }

    /** An AGGREGATE over the window sees every super-group row, subtotals included. */
    @Test
    public void anAggregateWindowSeesTheSubtotalRows() {
        assertEquals("1/220,2/220,3/220,4/220,null/220", answer(
            "SELECT a, SUM(SUM(b)) OVER () FROM gw GROUP BY ROLLUP(a) ORDER BY a"),
            "110 of detail plus the 110 grand total — the subtotal row is a row like any other");
        assertEquals("1/110,2/110,3/110,4/110,null/110", answer(
            "SELECT a, MAX(SUM(b)) OVER () FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
        assertEquals("1/60,2/10,3/110,4/30,null/220", answer(
            "SELECT a, SUM(SUM(b)) OVER (ORDER BY SUM(b)) FROM gw GROUP BY ROLLUP(a) ORDER BY a"),
            "and a running total follows the window's own order, not the group's");
    }

    /** A PARTITION over a two-dimension rollup numbers within each partition. */
    @Test
    public void aPartitionedWindowOverATwoDimensionRollup() {
        assertEquals("1/x/2,2/x/1,null/x/3,3/y/2,4/y/1,null/y/3,null/null/1", answer(
            "SELECT a, k, ROW_NUMBER() OVER (PARTITION BY k ORDER BY SUM(b)) FROM gw"
                + " GROUP BY ROLLUP(k, a) ORDER BY k, a"),
            "each k holds two details and a subtotal, and the subtotal is last because it is largest");
    }

    /** Ordering by a DIMENSION rather than an aggregate still works, NULL group last. */
    @Test
    public void orderingByTheDimensionItself() {
        assertEquals("1/1,2/2,3/3,4/4,null/5", answer(
            "SELECT a, ROW_NUMBER() OVER (ORDER BY a) FROM gw GROUP BY ROLLUP(a) ORDER BY a"));
    }
}
