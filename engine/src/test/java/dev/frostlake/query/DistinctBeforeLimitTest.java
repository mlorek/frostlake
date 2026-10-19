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
 * SELECT DISTINCT with TOP, LIMIT, OFFSET or FETCH removes the duplicates before it counts the rows
 * (live-verified), in a query, a CTAS and a derived table alike.
 */
public class DistinctBeforeLimitTest extends MeasuredCellsTestSupport {

    @Test
    public void theLimitCountsDistinctRows() {
        engine.execute("CREATE OR REPLACE TABLE dt (a INT)");
        engine.execute("INSERT INTO dt VALUES (1), (1), (1), (2), (3)");
        assertCells(new String[][] {
            {"SELECT DISTINCT a FROM dt ORDER BY a LIMIT 2", "1 ;; 2"},
            {"SELECT DISTINCT a FROM dt ORDER BY a LIMIT 2 OFFSET 1", "2 ;; 3"},
            {"SELECT DISTINCT TOP 2 a FROM dt ORDER BY a", "1 ;; 2"},
            {"SELECT DISTINCT a FROM dt ORDER BY a FETCH FIRST 2 ROWS ONLY", "1 ;; 2"},
            {"CREATE OR REPLACE TABLE dc2 AS SELECT DISTINCT a FROM dt ORDER BY a LIMIT 2",
                "Table DC2 successfully created."},
            {"SELECT * FROM dc2 ORDER BY 1", "1 ;; 2"},
            {"SELECT COUNT(*) FROM (SELECT DISTINCT a FROM dt LIMIT 2)", "2"},
        });
    }
}
