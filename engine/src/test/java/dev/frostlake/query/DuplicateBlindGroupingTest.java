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
 * Under a reader blind to duplicates, a block grouping by plain values only folds duplicate rows and loses its
 * grouping, a block passing its rows through reads its relation as such a reader, and a DISTINCT that sorts
 * compares its rows in their own order (all live-verified).
 */
public class DuplicateBlindGroupingTest extends MeasuredCellsTestSupport {

    @Test
    public void groupedAndSortedBodies() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) GROUP BY x, y) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) ORDER BY y) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY y, x) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY x, "
                + "y) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) ORDER BY x) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct)) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER "
                + "BY y) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT COUNT(DISTINCT y) FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) GROUP BY x, y)");
    }

    @Test
    public void moreShapes() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci', d VARCHAR COLLATE 'en-cs')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a', 'a'), ('A', 'A', 'A'), ('b', 'b', 'b'), ('B', 'B', 'B')");
        engine.execute("CREATE OR REPLACE VIEW vg AS SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, "
            + "s FROM ct) GROUP BY x, y");
        assertAnswer("A ;; B ;; a ;; b",
            "WITH g AS (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY x, y) "
                + "SELECT DISTINCT y FROM g ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b", "SELECT DISTINCT y FROM vg ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP "
                + "BY x, y) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y, 1 AS k FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) GROUP BY x, y) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y HAVING TRUE) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y ORDER BY y) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY 1, 2) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY ALL) ORDER BY 1");
        assertAnswer("b",
            "SELECT MAX(y) FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) GROUP BY "
                + "x, y)");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x AS x2, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) GROUP BY x, y) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y LIMIT 10) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT * FROM (SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER "
                + "BY y) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT * FROM (SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) LIMIT "
                + "10) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT * FROM (SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)) ORDER "
                + "BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY y");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)) ORDER "
                + "BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct)) "
                + "ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) ORDER BY y LIMIT 10) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "WITH w AS (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) ORDER BY y) "
                + "SELECT DISTINCT y FROM w ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT DISTINCT y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) ORDER BY y) ORDER BY 1");
        assertAnswer("B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y) WHERE y > 'A' ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY GROUPING SETS ((x, y))) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT x FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 113 | found QUALIFY clause but no "
                + "window function.",
            "SELECT DISTINCT y FROM (SELECT x, y FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y QUALIFY TRUE) ORDER BY 1");
        assertAnswer("A ;; B ;; a ;; b",
            "SELECT DISTINCT y FROM (SELECT y, x FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM ct) "
                + "GROUP BY x, y) ORDER BY 1");
        assertAnswer("REFUSED Numeric value 'a' is not recognized",
            "SELECT DISTINCT y FROM (SELECT DISTINCT * FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 1, s FROM "
                + "ct) ORDER BY 1) ORDER BY 1");
    }
}
