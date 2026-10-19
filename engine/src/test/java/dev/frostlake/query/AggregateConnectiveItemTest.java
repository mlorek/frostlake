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
 * A select item that joins comparisons of aggregates with AND, OR or NOT makes the query an aggregate one, as an
 * item holding an aggregate anywhere else does: {@code SELECT MIN(r) >= 1 AND MAX(r) <= 4 FROM t} answers one row,
 * the connective over the group's aggregates, typed BOOLEAN, and a column of no group beside the aggregates is
 * "not a valid group by expression" (live-verified).
 */
public class AggregateConnectiveItemTest extends MeasuredCellsTestSupport {

    @Test
    public void aConnectiveOfAggregateComparisonsIsOneValueForTheGroup() {
        assertCells(new String[][] {
            {"SELECT MIN(r) >= 1 AND MAX(r) <= 4 FROM (SELECT 1 AS r)", "true"},
            {"SELECT (MIN(r) >= 1) AND (MAX(r) <= 4) FROM (SELECT 1 AS r)", "true"},
            {"SELECT MIN(r) = 1 OR MAX(r) = 4 FROM (SELECT 1 AS r)", "true"},
            {"SELECT NOT MIN(r) > 1 FROM (SELECT 1 AS r)", "true"},
            {"SELECT MIN(r) >= 1 AND TRUE FROM (SELECT 1 AS r)", "true"},
            {"SELECT COUNT(*) > 0 AND MAX(r) = 1 FROM (SELECT 1 AS r)", "true"},
            {"SELECT MIN(r) + MAX(r) FROM (SELECT 1 AS r)", "2"},
        });
        engine.execute("CREATE OR REPLACE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertCells(new String[][] {
            {"SELECT MAX(i) >= 1 AND MIN(i) <= 4, COUNT(*) FROM g", "true|1000"},
            {"SELECT MOD(i, 3) k, MIN(i) < 5 AND MAX(i) > 990 b FROM g GROUP BY k ORDER BY k",
                "0|true ;; 1|true ;; 2|true"},
            {"SELECT SUM(i) > 0 OR NOT COUNT(*) = 0 FROM g", "true"},
            {"SELECT CASE WHEN MIN(i) = 0 AND MAX(i) = 999 THEN 'y' ELSE 'n' END FROM g", "y"},
            {"SELECT IFF(MIN(i) = 0 AND MAX(i) = 999, 1, 0) FROM g", "1"},
            {"SELECT (MIN(i) = 0 AND MAX(i) = 999) = TRUE FROM g", "true"},
        });
        assertShape("B:BOOLEAN", "SELECT MIN(i) >= 0 AND MAX(i) < 1000 AS b FROM g");
        assertCells(new String[][] {
            {"SELECT MIN(i) = 0 AND i = 1 FROM g",
                "REFUSED SQL compilation error: | [G.I] is not a valid group by expression"},
        });
    }

    @Test
    public void aConnectiveItemReadsItsGroupsAggregatesAndKeys() {
        engine.execute("CREATE OR REPLACE TABLE g AS SELECT SEQ4() AS i FROM TABLE(GENERATOR(ROWCOUNT => 1000))");
        assertCells(new String[][] {
            {"SELECT MIN(i) = 0 AND i = 1 FROM g",
                "REFUSED SQL compilation error: | [G.I] is not a valid group by expression"},
            {"SELECT i, MIN(i) > 0 OR TRUE FROM g GROUP BY i ORDER BY i LIMIT 2", "0|true ;; 1|true"},
            {"SELECT NOT (COUNT(*) > 0) FROM g", "false"},
            {"SELECT COUNT(*) > 0 AND MAX(i) IS NOT NULL FROM g", "true"},
            {"SELECT MIN(i) BETWEEN 0 AND 1 AND MAX(i) > 0 FROM g", "true"},
            {"SELECT MAX(i) > 0 AND MIN(i) >= 0 FROM g WHERE i < 0", "null"},
            {"SELECT COUNT(*) = 0 OR MAX(i) > 0 FROM g WHERE i < 0", "true"},
            {"SELECT MIN(i) = 0 AND MAX(i) = 999 b FROM g HAVING b", "true"},
            {"SELECT MIN(i) > 0 AND MAX(i) > 0, COUNT(*) OVER () FROM g", "false|1"},
            {"SELECT MOD(i, 2) k, MIN(i) < 2 AND COUNT(*) = 500 FROM g GROUP BY k ORDER BY k", "0|true ;; 1|true"},
            {"SELECT COUNT(*) > 0 AND i > 0 FROM g GROUP BY i ORDER BY 1 LIMIT 1", "false"},
            {"SELECT MIN(i) = 0 AND MAX(i) = 999 FROM g GROUP BY ALL", "true"},
        });
    }
}
