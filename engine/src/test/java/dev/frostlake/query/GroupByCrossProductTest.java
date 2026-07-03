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
 * Multiple GROUP BY super-group elements combine as a CROSS PRODUCT of their grouping sets, not a flat union.
 * {@code ROLLUP(a), ROLLUP(g)} = grouping sets {(a,g),(a),(g),()} — not {(a),(),(g),()} (which duplicated the
 * grand total and dropped the (a,g) detail rows).
 */
public class GroupByCrossProductTest extends BaseDatabaseTest {

    @Test
    public void twoRollupsCrossProductNotUnion() {
        engine.execute("CREATE TABLE gt (a VARCHAR, g VARCHAR, v INTEGER)");
        engine.execute("INSERT INTO gt VALUES ('x','p',1), ('x','q',2), ('y','p',3)");

        final ResultSet rs = engine.executeQuery("SELECT a, g, SUM(v) FROM gt GROUP BY ROLLUP(a), ROLLUP(g)");

        // (a,g): 3 rows; (a): 2; (g): 2; (): 1  ->  8 rows, exactly one grand total (SUM = 6).
        assertEquals(8, rs.getRows().size());
        int grandTotals = 0;
        for (final Row r : rs.getRows()) {
            if (((Number) r.getValue(2)).longValue() == 6L) {
                grandTotals++;
            }
        }
        assertEquals(1, grandTotals);
    }
}
