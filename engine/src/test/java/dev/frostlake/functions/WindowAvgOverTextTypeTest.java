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
 * A window AVG over a VARIANT or a text is a FLOAT, as the grouped AVG over it is, and so is a window SUM over one
 * (live-verified); a running AVG over the rows in order averages the rows read so far.
 */
public class WindowAvgOverTextTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aWindowAverageOfAVariantOrATextIsAFloat() {
        engine.execute("CREATE OR REPLACE TABLE w (n INT, s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO w SELECT 1, '1.5', PARSE_JSON('1.5')");
        engine.execute("INSERT INTO w SELECT 2, '2', PARSE_JSON('2')");
        engine.execute("CREATE OR REPLACE TABLE w2 AS SELECT AVG(s) OVER () a, AVG(v) OVER () b FROM w");
        assertCells(new String[][] {
            {"SELECT AVG(v), SYSTEM$TYPEOF(AVG(v)) FROM w", "1.75|FLOAT[DOUBLE]"},
            {"SELECT DISTINCT a, b FROM w2", "1.75|1.75"},
            {"SELECT DISTINCT SYSTEM$TYPEOF(a), SYSTEM$TYPEOF(b) FROM w2", "FLOAT[DOUBLE]|FLOAT[DOUBLE]"},
            {"SELECT DISTINCT SYSTEM$TYPEOF(AVG(v) OVER ()), SYSTEM$TYPEOF(AVG(s) OVER ()) FROM w",
                "FLOAT[DOUBLE]|FLOAT[DOUBLE]"},
            {"SELECT AVG(v) OVER (ORDER BY n) FROM w ORDER BY n", "1.5 ;; 1.75"},
            {"SELECT DISTINCT SYSTEM$TYPEOF(SUM(v) OVER ()), SYSTEM$TYPEOF(SUM(s) OVER ()) FROM w",
                "FLOAT[DOUBLE]|FLOAT[DOUBLE]"},
        });
    }
}
