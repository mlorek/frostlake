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
 * An ORDER BY ordinal counts the columns a star with EXCLUDE, REPLACE, RENAME or ILIKE projects (live-verified),
 * and an ordinal past them is refused: [3] is not a valid order by expression.
 */
public class ModifiedStarOrdinalTest extends MeasuredCellsTestSupport {

    @Test
    public void anOrdinalCountsTheStarsColumns() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT, c INT)");
        engine.execute("INSERT INTO t VALUES (2, 30, 200), (1, 20, 100), (3, 10, 300)");
        assertCells(new String[][] {
            {"SELECT * EXCLUDE a FROM t ORDER BY 2", "20|100 ;; 30|200 ;; 10|300"},
            {"SELECT * REPLACE (c * -1 AS c) FROM t ORDER BY 3", "3|10|-300 ;; 2|30|-200 ;; 1|20|-100"},
            {"SELECT * RENAME (c AS z) FROM t ORDER BY 3", "1|20|100 ;; 2|30|200 ;; 3|10|300"},
            {"SELECT * ILIKE '%' FROM t ORDER BY 2", "3|10|300 ;; 1|20|100 ;; 2|30|200"},
            {"SELECT * EXCLUDE a, a FROM t ORDER BY 2", "20|100|1 ;; 30|200|2 ;; 10|300|3"},
            {"SELECT * EXCLUDE a, -a x FROM t ORDER BY 3", "10|300|-3 ;; 30|200|-2 ;; 20|100|-1"},
            {"SELECT * EXCLUDE a FROM t ORDER BY 3",
                "REFUSED SQL compilation error: | [3] is not a valid order by expression"},
            {"SELECT t.* EXCLUDE (a, b) FROM t ORDER BY 1 DESC", "300 ;; 200 ;; 100"},
        });
    }
}
