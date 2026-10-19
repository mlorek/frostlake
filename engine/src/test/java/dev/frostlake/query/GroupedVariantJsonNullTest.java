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
 * A VARIANT JSON null is missing input for COUNT, SUM and AVG whether the query groups or not: grouped over
 * {JSON null, 3}, COUNT and COUNT(DISTINCT) are 1 and SUM and AVG are 3, as ungrouped, windowed and rolled up
 * (live-verified).
 */
public class GroupedVariantJsonNullTest extends MeasuredCellsTestSupport {

    @Test
    public void aGroupedCountSumAndAvgSkipAJsonNull() {
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE TABLE jg (g INT, v VARIANT)", "Table JG successfully created."},
                {"INSERT INTO jg SELECT 1, PARSE_JSON('null')", "1"},
                {"INSERT INTO jg SELECT 1, PARSE_JSON('3')", "1"},
                {"SELECT COUNT(v), SUM(v), AVG(v) FROM jg", "1|3.0|3.0"},
                {"SELECT g, COUNT(v), COUNT(DISTINCT v) FROM jg GROUP BY g", "1|1|1"},
                {"SELECT g, SUM(v) FROM jg GROUP BY g", "1|3.0"},
                {"SELECT g, AVG(v) FROM jg GROUP BY g", "1|3.0"},
                {"SELECT g, COUNT(v) OVER (PARTITION BY g), AVG(v) OVER (PARTITION BY g) FROM jg ORDER BY 1, 2",
                    "1|1|3.0 ;; 1|1|3.0"},
                {"SELECT g, AVG(v + 0), COUNT(v:x) FROM jg GROUP BY g", "1|3.0|0"},
                {"SELECT g, MIN(v), MAX(v), COUNT(*) FROM jg GROUP BY g", "1|3|null|2"},
                {"SELECT g, ARRAY_AGG(v), COUNT_IF(v IS NULL) FROM jg GROUP BY g", "1|[null,3]|0"},
                {"SELECT g, SUM(v), AVG(v), COUNT(v) FROM jg GROUP BY ROLLUP (g) ORDER BY g NULLS LAST",
                    "1|3.0|3.0|1 ;; null|3.0|3.0|1"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS jg");
        }
    }
}
