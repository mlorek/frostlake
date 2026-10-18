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
 * A ROLLUP, CUBE or GROUPING SETS super-group row reads a dimension its grouping set aggregates over as NULL
 * inside any expression over it (live-verified): COALESCE(r, 'ALL') labels the grand total ALL, r IS NULL is
 * true there, CONCAT(r, '-', p) and UPPER(r) are NULL, and a qualified or differently cased spelling of the key
 * reads NULL too.
 */
public class RollupRolledUpDimensionTest extends MeasuredCellsTestSupport {

    @Test
    public void aRolledUpDimensionReadsNull() {
        engine.execute("CREATE OR REPLACE TABLE s (r VARCHAR, p VARCHAR, amt INT)");
        engine.execute("INSERT INTO s VALUES ('E', 'a', 1), ('E', 'b', 2), ('W', 'a', 3), ('W', NULL, 4)");
        assertCells(new String[][] {
            {"SELECT COALESCE(r, 'ALL') rr, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 2", "E|3 ;; W|7 ;; ALL|10"},
            {"SELECT r, COALESCE(r, 'ALL') rr, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 3",
                "E|E|3 ;; W|W|7 ;; null|ALL|10"},
            {"SELECT r IS NULL n, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 2", "false|3 ;; false|7 ;; true|10"},
            {"SELECT r, p, CONCAT(r, '-', p) rp FROM s GROUP BY ROLLUP (r, p) ORDER BY 1 NULLS LAST, 2 NULLS LAST",
                "E|a|E-a ;; E|b|E-b ;; E|null|null ;; W|a|W-a ;; W|null|null ;; W|null|null ;; null|null|null"},
            {"SELECT R, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 1 NULLS LAST", "E|3 ;; W|7 ;; null|10"},
            {"SELECT s.r, SUM(amt) FROM s GROUP BY ROLLUP (r) ORDER BY 1 NULLS LAST", "E|3 ;; W|7 ;; null|10"},
            {"SELECT UPPER(r) u, SUM(amt) FROM s GROUP BY CUBE (r) ORDER BY 2", "E|3 ;; W|7 ;; null|10"},
            {"SELECT r || '!' x, SUM(amt) FROM s GROUP BY GROUPING SETS ((r), ()) ORDER BY 2",
                "E!|3 ;; W!|7 ;; null|10"},
        });
    }
}
