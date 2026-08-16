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
 * GROUPING() with multiple arguments returns an integer bitmask (first argument = most significant
 * bit), each bit 1 when that column is aggregated over (rolled up) and 0 when grouped by. Previously
 * a multi-argument call collapsed to a single 0/1 (and always 1, since the "a, b" arg matched no key).
 * Argument matching is now case-insensitive. A single argument still yields 0 or 1.
 */
public class GroupingBitmaskTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE sales (region VARCHAR, product VARCHAR, amount INTEGER)");
        engine.execute("INSERT INTO sales VALUES ('East','A',10), ('East','B',20), ('West','A',30)");
    }

    @Test
    public void multiArgumentGroupingIsABitmask() {
        final ResultSet rs = engine.executeQuery(
            "SELECT region, product, GROUPING(region, product) AS grp FROM sales GROUP BY ROLLUP(region, product)");
        int detail = 0;      // grp 0 — both grouped
        int regionSub = 0;   // grp 1 — region grouped, product rolled up
        int grandTotal = 0;  // grp 3 — both rolled up
        for (final Row r : rs.getRows()) {
            switch ((int) ((Number) r.getValue(2)).longValue()) {
                case 0: detail++;
                break;
                case 1: regionSub++;
                break;
                case 3: grandTotal++;
                break;
                default: break;
            }
        }
        assertEquals(3, detail);
        assertEquals(2, regionSub);
        assertEquals(1, grandTotal);
    }

    @Test
    public void singleArgumentGroupingTracksEachColumn() {
        final ResultSet rs = engine.executeQuery(
            "SELECT region, product, GROUPING(region) AS gr, GROUPING(product) AS gp "
            + "FROM sales GROUP BY ROLLUP(region, product)");
        for (final Row r : rs.getRows()) {
            final long gr = ((Number) r.getValue(2)).longValue();
            final long gp = ((Number) r.getValue(3)).longValue();
            // A column's GROUPING bit is 1 exactly when that column is rolled up (its value is NULL).
            assertEquals(r.getValue(0) == null ? 1L : 0L, gr);
            assertEquals(r.getValue(1) == null ? 1L : 0L, gp);
        }
    }

    @Test
    public void groupingArgumentIsCaseInsensitive() {
        final ResultSet rs = engine.executeQuery(
            "SELECT GROUPING(REGION) AS gr FROM sales GROUP BY ROLLUP(region)");
        boolean sawGrouped = false;
        boolean sawRolledUp = false;
        for (final Row r : rs.getRows()) {
            final long gr = ((Number) r.getValue(0)).longValue();
            sawGrouped |= gr == 0L;
            sawRolledUp |= gr == 1L;
        }
        assertTrue(sawGrouped && sawRolledUp, "GROUPING(REGION) must match GROUP BY region case-insensitively");
    }
}
