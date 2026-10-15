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
package dev.frostlake.functions.vector;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The numeric aggregates refuse a VECTOR while the statement compiles, each in the words it refuses a semi-structured
 * value in: SUM and AVG list their argument types (an AVG over an ordered window in SUM's name), the deviations and
 * variances are refused as the product they compute, MEDIAN and the percentiles as incompatible with their NUMBER
 * accumulator, and MIN and MAX as a type they do not support (live-verified).
 */
public class VectorAggregateRefusalTest extends MeasuredCellsTestSupport {

    private static final String LISTS = "REFUSED SQL compilation error: error line 1 at position 7 | "
        + "Invalid argument types for function ";
    private static final String V = "(VECTOR(FLOAT, 2))";
    private static final String PRODUCT = LISTS + "'*': (VECTOR(FLOAT, 2), VECTOR(FLOAT, 2))";
    private static final String INCOMPATIBLE =
        "REFUSED SQL compilation error: | incompatible types: [VECTOR(FLOAT, 2)] and [NUMBER(9,0)]";

    @Test
    public void theNumericAggregatesRefuseAVector() {
        try {
            assertCells(new String[][] {
                {"CREATE OR REPLACE TABLE vf (v VECTOR(FLOAT, 2), o OBJECT, n INT)", "Table VF successfully created."},
                {"INSERT INTO vf SELECT [1, 2]::VECTOR(FLOAT, 2), {'a': 1}, 1", "1"},
                {"SELECT SUM(v) FROM vf", LISTS + "'SUM': " + V},
                {"SELECT AVG(v) FROM vf", LISTS + "'SUM': " + V},
                {"SELECT SUM(DISTINCT v) FROM vf", LISTS + "'SUM': " + V},
                {"SELECT SUM(v) OVER () FROM vf", LISTS + "'SUM': " + V},
                {"SELECT AVG(v) OVER () FROM vf", LISTS + "'AVG': " + V},
                {"SELECT AVG(v) OVER (PARTITION BY n) FROM vf", LISTS + "'AVG': " + V},
                {"SELECT AVG(v) OVER (ORDER BY n) FROM vf", LISTS + "'SUM': " + V},
                {"SELECT AVG(DISTINCT v) FROM vf", LISTS + "'AVG': " + V},
                {"SELECT AVG(o) OVER (ORDER BY n) FROM vf", LISTS + "'SUM': (OBJECT)"},
                {"SELECT AVG(o) OVER () FROM vf", LISTS + "'AVG': (OBJECT)"},
                {"SELECT STDDEV(v) FROM vf", PRODUCT},
                {"SELECT VARIANCE(v) FROM vf", PRODUCT},
                {"SELECT KURTOSIS(v) FROM vf", PRODUCT},
                {"SELECT SKEW(v) FROM vf", PRODUCT},
                {"SELECT STDDEV_POP(v), VAR_SAMP(v) FROM vf", PRODUCT},
                {"SELECT MEDIAN(v) FROM vf", INCOMPATIBLE},
                {"SELECT MEDIAN(v) OVER () FROM vf", INCOMPATIBLE},
                {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY v) FROM vf", INCOMPATIBLE},
                {"SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY v) FROM vf", INCOMPATIBLE},
                {"SELECT APPROX_PERCENTILE(v, 0.5) FROM vf", "REFUSED SQL compilation error: error line 0 at position "
                    + "-1 | Invalid argument types for function 'APPROX_PERCENTILE_ACCUMULATE': " + V},
                {"SELECT COUNT(v), MIN(v), MAX(v) FROM vf",
                    "REFUSED SQL compilation error: | Function MIN does not support VECTOR(FLOAT, 2) argument type"},
                {"SELECT MAX(v) FROM vf",
                    "REFUSED SQL compilation error: | Function MAX does not support VECTOR(FLOAT, 2) argument type"},
            });
        } finally {
            engine.execute("DROP TABLE IF EXISTS vf");
        }
    }
}
