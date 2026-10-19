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
 * FLOAT SUM and AVG answer as IEEE addition does past an infinity (live-verified): two 1e308 values or 'inf'
 * beside 1 sum to Infinity, '-inf' beside 1 to -Infinity, and 'inf' beside '-inf' to NaN, grouped and over a
 * window alike.
 */
public class FloatSumInfinityTest extends MeasuredCellsTestSupport {

    @Test
    public void anInfinityIsKept() {
        engine.execute("CREATE OR REPLACE TABLE fit (f FLOAT, g INT)");
        engine.execute("INSERT INTO fit VALUES (1e308, 1), (1e308, 1), (1, 2), ('inf', 3), (1, 3)");
        assertCells(new String[][] {
            {"SELECT 1e308::FLOAT + 1e308::FLOAT", "Infinity"},
            {"SELECT g, SUM(f), AVG(f) FROM fit GROUP BY g ORDER BY g",
                "1|Infinity|Infinity ;; 2|1.0|1.0 ;; 3|Infinity|Infinity"},
            {"SELECT g, SUM(f) OVER (PARTITION BY g) FROM fit ORDER BY g",
                "1|Infinity ;; 1|Infinity ;; 2|1.0 ;; 3|Infinity ;; 3|Infinity"},
            {"SELECT g, f, SUM(f) OVER (PARTITION BY g ORDER BY f ROWS UNBOUNDED PRECEDING) FROM fit ORDER BY g, f",
                "1|1.0E308|1.0E308 ;; 1|1.0E308|Infinity ;; 2|1.0|1.0 ;; 3|1.0|1.0 ;; 3|Infinity|Infinity"},
            {"SELECT SUM(f), AVG(f) FROM (SELECT '-inf'::FLOAT f UNION ALL SELECT 'inf'::FLOAT)", "NaN|NaN"},
            {"SELECT SUM(f) FROM (SELECT '-inf'::FLOAT f UNION ALL SELECT 1::FLOAT)", "-Infinity"},
            {"SELECT SUM(f) FROM (SELECT 'NaN'::FLOAT f UNION ALL SELECT 1::FLOAT)", "NaN"},
        });
    }
}
