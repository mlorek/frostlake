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

import org.junit.jupiter.api.Test;

/**
 * GROUP BY ALL beside a window call: a column the window reads that nothing groups is refused first, as with the
 * keys written out, and the window item itself only where it reads grouped columns alone; a window over
 * aggregates groups as any aggregate item does (all live-verified).
 */
public class GroupByAllWindowRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void anUngroupedColumnAWindowReadsIsRefusedFirst() {
        assertAnswer("Table S729 successfully created.",
            "CREATE OR REPLACE TABLE s729 (r VARCHAR, p VARCHAR, amt INT)");
        assertAnswer("4", "INSERT INTO s729 VALUES ('E', 'a', 1), ('E', 'b', 2), ('W', 'a', 3), ('W', NULL, 4)");
        assertAnswer("REFUSED SQL compilation error: | [S729.AMT] is not a valid group by expression",
            "SELECT r, SUM(amt) OVER () w FROM s729 GROUP BY ALL");
        assertAnswer("REFUSED SQL compilation error: | [S729.AMT] is not a valid group by expression",
            "SELECT r, ROW_NUMBER() OVER (ORDER BY amt DESC) rn FROM s729 GROUP BY ALL ORDER BY r");
        assertAnswer("REFUSED SQL compilation error: | [S729.AMT] is not a valid group by expression",
            "SELECT r, SUM(amt) OVER () w FROM s729 GROUP BY r");
        assertAnswer("E|10 ;; W|10", "SELECT r, SUM(SUM(amt)) OVER () w FROM s729 GROUP BY ALL ORDER BY r");
        assertAnswer("REFUSED SQL compilation error: | [RK] is not a valid group by expression",
            "SELECT r, RANK() OVER (ORDER BY r) rk FROM s729 GROUP BY ALL ORDER BY r");
        assertAnswer("E|2|4 ;; W|2|4",
            "SELECT r, COUNT(*) c, SUM(COUNT(*)) OVER () t FROM s729 GROUP BY ALL ORDER BY r");
        assertAnswer("REFUSED SQL compilation error: | [RN] is not a valid group by expression",
            "SELECT r, p, ROW_NUMBER() OVER (PARTITION BY r ORDER BY p) rn FROM s729 GROUP BY ALL ORDER BY r, p");
        assertAnswer("REFUSED SQL compilation error: | [S729.AMT] is not a valid group by expression",
            "SELECT r, LAG(amt) OVER (ORDER BY r) l FROM s729 GROUP BY ALL");
        assertAnswer("REFUSED SQL compilation error: | [S729.AMT] is not a valid group by expression",
            "SELECT r, MAX(amt) OVER (PARTITION BY p) m FROM s729 GROUP BY ALL");
        assertAnswer("REFUSED SQL compilation error: | [S729.P] is not a valid group by expression",
            "SELECT r, FIRST_VALUE(p) OVER (ORDER BY r) f FROM s729 GROUP BY ALL");
        assertAnswer("REFUSED SQL compilation error: | [S729.AMT] is not a valid group by expression",
            "SELECT r, SUM(amt) OVER (PARTITION BY r) w FROM s729 GROUP BY ALL");
        assertAnswer("E|1.500000|1 ;; W|3.500000|2",
            "SELECT r, AVG(amt) a, RANK() OVER (ORDER BY AVG(amt)) rk FROM s729 GROUP BY ALL ORDER BY r");
        assertAnswer("REFUSED SQL compilation error: | [S729.P] is not a valid group by expression",
            "SELECT UPPER(r) u, COUNT(*) OVER (PARTITION BY p) c FROM s729 GROUP BY ALL");
    }
}
