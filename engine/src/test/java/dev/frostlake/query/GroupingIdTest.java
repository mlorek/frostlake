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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GROUPING_ID(e1, …, en) returns the integer bit-vector for the current grouping set: e1 is the most
 * significant bit, each ei is 1 when it is rolled up (aggregated over) and 0 when grouped by. Over
 * ROLLUP(region, product) the detail rows are 0, region-subtotals 1 (product rolled up), and the grand
 * total 3 (both rolled up). GROUPING_ID(a, b) equals GROUPING(a, b) for the same arguments.
 */
public class GroupingIdTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('East','A',10), ('East','B',20), ('West','A',30)");
    }

    @Test
    public void groupingIdIsABitmaskOverRollup() {
        final ResultSet rs = engine.executeQuery(
            "SELECT region, product, GROUPING_ID(region, product) AS grp FROM sales "
            + "GROUP BY ROLLUP(region, product)");
        int detail = 0;      // grp 0 — both grouped
        int regionSub = 0;   // grp 1 — region grouped, product rolled up
        int grandTotal = 0;  // grp 3 — both rolled up
        for (final Row r : rs.getRows()) {
            switch ((int) ((Number) r.getValue(2)).longValue()) {
                case 0: detail++; break;
                case 1: regionSub++; break;
                case 3: grandTotal++; break;
                default: break;
            }
        }
        assertEquals(3, detail);
        assertEquals(2, regionSub);
        assertEquals(1, grandTotal);
    }

    @Test
    public void singleArgumentTracksRolledUpColumn() {
        final ResultSet rs = engine.executeQuery(
            "SELECT region, GROUPING_ID(region) AS gid FROM sales GROUP BY ROLLUP(region)");
        boolean sawGrouped = false;
        boolean sawRolledUp = false;
        for (final Row r : rs.getRows()) {
            final long gid = ((Number) r.getValue(1)).longValue();
            // A column's GROUPING_ID bit is 1 exactly when it is rolled up (its value is NULL).
            assertEquals(r.getValue(0) == null ? 1L : 0L, gid);
            sawGrouped |= gid == 0L;
            sawRolledUp |= gid == 1L;
        }
        assertTrue(sawGrouped && sawRolledUp);
    }

    @Test
    public void groupingIdEqualsGroupingForSameArguments() {
        final ResultSet rs = engine.executeQuery(
            "SELECT GROUPING(region, product) AS g, GROUPING_ID(region, product) AS gid FROM sales "
            + "GROUP BY ROLLUP(region, product)");
        for (final Row r : rs.getRows()) {
            assertEquals(((Number) r.getValue(0)).longValue(), ((Number) r.getValue(1)).longValue());
        }
    }
}
