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
 * A GENERATOR bound over a derived relation's column the statistics pin to one value: an expression over pinned
 * columns, MAX, MIN and COUNT over a whole table, DISTINCT and GROUP BY of a pinned column, a UNION of one value, a
 * CTE read as its body; the SYS_VW names a refusal gives an outer join's columns and a set-operation CTE's; EXPLAIN's
 * Generator counting a pinned column's rows (all live-verified).
 */
public class GeneratorBoundDerivedPinsTest extends MeasuredCellsTestSupport {

    @Test
    public void derivedPins() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE g3 (n INT)");
        engine.execute("INSERT INTO g3 VALUES (5)");
        engine.execute("INSERT INTO g3 VALUES (5)");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT n + 1 AS n FROM g1) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("3", "SELECT COUNT(*) FROM (SELECT MAX(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("5", "SELECT COUNT(*) FROM (SELECT DISTINCT n FROM g3) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT n * 2 AS n FROM g1) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT MIN(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT COUNT(*) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT SUM(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT n + 1 AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT DISTINCT n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("4", "SELECT COUNT(*) FROM (SELECT MAX(n) + 1 AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("1", "SELECT COUNT(*) FROM (SELECT ABS(n) AS n FROM g1) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("1", "SELECT COUNT(*) FROM (SELECT n FROM g1 WHERE n > 0) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("5", "SELECT COUNT(*) FROM (SELECT n FROM g3 GROUP BY n) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("3", "SELECT COUNT(*) FROM (SELECT 3 AS n) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("3", "SELECT COUNT(*) FROM (SELECT n + 1 AS n FROM g1) d, TABLE(GENERATOR(ROWCOUNT => d.n + 1))");
        assertAnswer("8",
            "WITH c AS (SELECT 4 AS n UNION ALL SELECT 4) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n))");
        assertAnswer("4", "WITH c AS (SELECT 4 AS n) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SYS_VW.N_0'",
            "WITH c AS (SELECT 4 AS n UNION ALL SELECT 5) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n))");
        assertAnswer("4",
            "WITH c AS (SELECT 4 AS n UNION SELECT 4) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n))");
        assertAnswer("2",
            "WITH c AS (SELECT n + 1 AS n FROM g1) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SYS_VW.N_1 + 1'",
            "SELECT COUNT(*) FROM g2 LEFT JOIN g1 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("0", "SELECT COUNT(*) FROM g2 JOIN g1 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("1", "SELECT COUNT(*) FROM g1 LEFT JOIN g2 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("GlobalStats: |     partitionsTotal=1 |     partitionsAssigned=0 |     bytesAssigned=0 | "
                + "Operations: | 1:0     ->Result  COUNT(*)   | 1:1          ->Aggregate  aggExprs: [COUNT(*)]   | "
                + "1:2               ->CartesianJoin   | 1:3                    ->TableScan  TEST_DB.TEST_SCHEMA.G1  "
                + "{partitionsTotal=1, partitionsAssigned=0, bytesAssigned=0} | 1:4                    ->Generator  2 "
                + "  | ",
            "EXPLAIN USING TEXT SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|CartesianJoin|"
                + "null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.G1|null|null|1|0|0 ;; 1|4|"
                + "[2]|Generator|null|null|2|null|null|null",
            "EXPLAIN SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))");
    }

    @Test
    public void morePins() {
        engine.execute("CREATE OR REPLACE TABLE g1 (n INT)");
        engine.execute("INSERT INTO g1 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE g2 (n INT)");
        engine.execute("INSERT INTO g2 VALUES (2), (3)");
        engine.execute("CREATE OR REPLACE TABLE g3 (n INT)");
        engine.execute("INSERT INTO g3 VALUES (5)");
        engine.execute("INSERT INTO g3 VALUES (5)");
        engine.execute("CREATE OR REPLACE TABLE g6 (n INT, m INT)");
        engine.execute("INSERT INTO g6 VALUES (2, 7)");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT 4 AS n UNION ALL SELECT 5) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SYS_VW.N_0'",
            "SELECT COUNT(*) FROM g2 LEFT JOIN g1 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g2.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SYS_VW.N_1'",
            "SELECT COUNT(*) FROM g6 LEFT JOIN g1 ON g1.n = g6.n, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("2", "SELECT COUNT(*) FROM (SELECT COUNT(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT MAX(n) AS n FROM g2 WHERE n > 2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT MAX(n) AS n FROM g2 GROUP BY n) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("2",
            "SELECT COUNT(*) FROM (SELECT n FROM g1 UNION ALL SELECT n FROM g1) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("4", "SELECT COUNT(*) FROM (SELECT MAX(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n + 1))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT AVG(n) AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT LENGTH('ab') AS n) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("5",
            "SELECT COUNT(*) FROM (SELECT n FROM (SELECT DISTINCT n FROM g3)) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("3",
            "WITH c AS (SELECT MAX(n) AS n FROM g2) SELECT COUNT(*) FROM c, TABLE(GENERATOR(ROWCOUNT => c.n))");
        assertAnswer("4",
            "WITH c AS (SELECT 4 AS n), e AS (SELECT n FROM c) SELECT COUNT(*) FROM e, TABLE(GENERATOR(ROWCOUNT => "
                + "e.n))");
        assertAnswer("1", "SELECT COUNT(*) FROM (SELECT MIN(n) - 1 AS n FROM g2) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("3",
            "SELECT COUNT(*) FROM (SELECT COUNT(*) + 1 AS n FROM g3) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("12", "SELECT COUNT(*) FROM (SELECT n + 1 AS n FROM g3) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("5", "SELECT COUNT(*) FROM (SELECT 2 AS n, 3 AS m) d, TABLE(GENERATOR(ROWCOUNT => d.n + d.m))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'D.N'",
            "SELECT COUNT(*) FROM (SELECT 4 AS n UNION SELECT 5) d, TABLE(GENERATOR(ROWCOUNT => d.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SYS_VW.N_0'",
            "SELECT COUNT(*) FROM g1 FULL JOIN g2 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("REFUSED SQL compilation error: | argument 1 to function GENERATOR needs to be constant, found "
                + "'SYS_VW.N_0'",
            "SELECT COUNT(*) FROM g1 RIGHT JOIN g2 ON g1.n = g2.n, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|CartesianJoin|"
                + "null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.G2|null|null|1|0|0 ;; 1|4|"
                + "[2]|Generator|null|null|5|null|null|null",
            "EXPLAIN SELECT COUNT(*) FROM g2, TABLE(GENERATOR(ROWCOUNT => 5))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|CartesianJoin|"
                + "null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.G1|null|null|1|0|0 ;; 1|4|"
                + "[2]|Generator|null|null|1|null|null|null",
            "EXPLAIN SELECT COUNT(*) FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|CartesianJoin|"
                + "null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.G3|null|null|1|0|0 ;; 1|4|"
                + "[2]|Generator|null|null|5|null|null|null",
            "EXPLAIN SELECT COUNT(*) FROM g3, TABLE(GENERATOR(ROWCOUNT => g3.n))");
        assertAnswer("null|null|null|GlobalStats|null|null|null|1|0|0 ;; 1|0|null|Result|null|null|COUNT(*)|null|null|"
                + "null ;; 1|1|[0]|Aggregate|null|null|aggExprs: [COUNT(*)]|null|null|null ;; 1|2|[1]|CartesianJoin|"
                + "null|null|null|null|null|null ;; 1|3|[2]|TableScan|TEST_DB.TEST_SCHEMA.G2|null|null|1|0|0 ;; 1|4|"
                + "[2]|Generator|null|null|3|null|null|null",
            "EXPLAIN SELECT COUNT(*) FROM TABLE(GENERATOR(ROWCOUNT => 3)), g2");
    }
}
