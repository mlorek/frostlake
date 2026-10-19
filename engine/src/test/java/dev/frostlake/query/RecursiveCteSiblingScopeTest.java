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
 * A recursive CTE's anchor and steps see the WITH's other CTEs everywhere, a scalar, IN or EXISTS subquery, a
 * nested WITH and a derived table included, and a table of a sibling CTE's name is not read in its place (all
 * live-verified).
 */
public class RecursiveCteSiblingScopeTest extends MeasuredCellsTestSupport {

    @Test
    public void aRecursiveCtesSubqueriesSeeTheSiblingCtes() {
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + (SELECT MAX(v) FROM x) FROM r "
                + "WHERE n < 3) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT (WITH d AS (SELECT v FROM x) SELECT MAX(v) FROM d) "
                + "UNION ALL SELECT n + 1 FROM r WHERE n < 3) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 AND n IN "
                + "(SELECT v FROM x UNION ALL SELECT 2)) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 AND "
                + "EXISTS (WITH d AS (SELECT v FROM x) SELECT v FROM d)) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + dd.v FROM r, (WITH d AS "
                + "(SELECT v FROM x) SELECT v FROM d) dd WHERE n < 3) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + x.v FROM r, x WHERE n < 3) "
                + "SELECT * FROM r ORDER BY n");
        assertAnswer("1", "WITH x AS (SELECT 1 v), y AS (SELECT (SELECT MAX(v) FROM x) m) SELECT * FROM y");
        assertAnswer("Table X successfully created.", "CREATE OR REPLACE TABLE x (v INT)");
        assertAnswer("1", "INSERT INTO x VALUES (50)");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + (SELECT MAX(v) FROM x) FROM r "
                + "WHERE n < 3) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 2 ;; 3",
            "WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 3 AND n IN "
                + "(SELECT v FROM x UNION ALL SELECT 2)) SELECT * FROM r ORDER BY n");
        assertAnswer("1 ;; 51 ;; 101",
            "WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + (SELECT MIN(v) FROM x) FROM r WHERE n < 100) "
                + "SELECT * FROM r ORDER BY n");
        answer("WITH RECURSIVE x AS (SELECT 1 v), r(n) AS (SELECT 1 UNION ALL SELECT n + (SELECT COUNT(*) FROM r) "
            + "FROM r WHERE n < 3) SELECT * FROM r ORDER BY n");
        assertAnswer("X successfully dropped.", "DROP TABLE x");
    }
}
