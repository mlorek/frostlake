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

package dev.frostlake.functions;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * The ordering aggregates over a VARIANT read a JSON null as a value that sorts after every number (live-verified):
 * MEDIAN and PERCENTILE_CONT count it, so a place on it or beside it answers NULL, and PERCENTILE_DISC picks it;
 * ordered DESC they leave it out; APPROX_COUNT_DISTINCT counts it as a value of its own.
 */
public class JsonNullPercentileTest extends MeasuredCellsTestSupport {

    @Test
    public void aJsonNullSortsAfterEveryNumber() {
        engine.execute("CREATE OR REPLACE TABLE jm (g INT, v VARIANT)");
        engine.execute("INSERT INTO jm SELECT 1, PARSE_JSON('null') UNION ALL SELECT 1, PARSE_JSON('3')");
        engine.execute("CREATE OR REPLACE TABLE jm3 (g INT, v VARIANT)");
        engine.execute("INSERT INTO jm3 SELECT 1, PARSE_JSON('null') UNION ALL SELECT 1, PARSE_JSON('3') UNION ALL "
            + "SELECT 1, PARSE_JSON('5')");
        engine.execute("CREATE OR REPLACE TABLE jm4 (g INT, v VARIANT)");
        engine.execute("INSERT INTO jm4 SELECT 1, PARSE_JSON('null') UNION ALL SELECT 1, PARSE_JSON('null') UNION "
            + "ALL SELECT 1, PARSE_JSON('3') UNION ALL SELECT 1, PARSE_JSON('5') UNION ALL SELECT 1, "
            + "PARSE_JSON('7')");
        assertCells(new String[][] {
            {"SELECT MEDIAN(v) FROM jm", "null"},
            {"SELECT g, MEDIAN(v) FROM jm GROUP BY g", "1|null"},
            {"SELECT MEDIAN(v) OVER (PARTITION BY g) FROM jm ORDER BY 1", "null ;; null"},
            {"SELECT MEDIAN(v) FROM jm3", "5.000"},
            {"SELECT g, PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY v) FROM jm3 GROUP BY g", "1|5.000"},
            {"SELECT g, MODE(v), APPROX_COUNT_DISTINCT(v), COUNT(*) FROM jm3 GROUP BY g", "1|null|3|3"},
            {"SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY v) FROM jm3", "5"},
            {"SELECT PERCENTILE_DISC(0.9) WITHIN GROUP (ORDER BY v) FROM jm3", "null"},
            {"SELECT PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY v DESC) FROM jm3", "4.500"},
            {"SELECT PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY v DESC) FROM jm3", "3.500"},
            {"SELECT MEDIAN(v), PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY v) FROM jm4", "7.000|5.000"},
            {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY v) OVER (PARTITION BY g) FROM jm3 ORDER BY 1",
                "5.000 ;; 5.000 ;; 5.000"},
            {"SELECT APPROX_COUNT_DISTINCT(v) FROM jm4", "4"},
            {"SELECT COUNT(DISTINCT v) FROM jm4", "3"},
            {"SELECT MEDIAN(v) FROM jm4 WHERE v IS NULL", "null"},
        });
    }
}
