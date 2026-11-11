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
 * An aggregate a scalar subquery writes over the outer row alone is the outer query's aggregate, a value the subquery
 * runs with (live-verified). A subquery whose select list opens with one, or whose HAVING holds one, aggregates
 * nothing of its own ahead of it and is refused as a subquery live cannot evaluate, over an outer table of one row
 * and over one whose rows hold one value alike, whatever its FROM, WHERE, GROUP BY or LIMIT: {@code MAX(o.i)},
 * {@code COUNT(o.i)}, {@code MAX(o.i) + 1}, {@code ARRAY_AGG(o.i)}, {@code MAX(o.i) + MAX(v)}. One whose own aggregate
 * comes first is answered, the outer aggregate read over the whole outer table on every row: over rows 0 and 1,
 * {@code (SELECT COUNT(*) + MAX(m.i) FROM g)} is 4 on both. An aggregate reading an inner column beside the outer row
 * is the subquery's own, answered row by row over one outer row, and over a derived table's single row the call is
 * judged as any other.
 */
public class OuterAggregateSubqueryTest extends MeasuredCellsTestSupport {

    @Test
    public void overAOneRowTable() {
        engine.execute("CREATE OR REPLACE TABLE o917 (i INT, a ARRAY)");
        engine.execute("INSERT INTO o917 SELECT 0, ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE g917 (k INT, v INT)");
        engine.execute("INSERT INTO g917 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE m917 (i INT)");
        engine.execute("INSERT INTO m917 VALUES (0), (1)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(o917.i) FROM (SELECT 1 AS k) GROUP BY k) FROM o917",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(o917.i) FROM (SELECT 1 AS k)) FROM o917",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(k) FROM (SELECT 1 AS k) GROUP BY k) FROM o917", "1"},
            {"SELECT (SELECT MAX(k + o917.i) FROM (SELECT 1 AS k) GROUP BY k) FROM o917", "1"},
            {"SELECT (SELECT MAX(k) FROM (SELECT 1 AS k) WHERE k = o917.i + 1 GROUP BY k) FROM o917", "1"},
            {"SELECT (SELECT MAX(v) FROM g917 WHERE g917.k = o917.i + 2 GROUP BY k) FROM o917", "30"},
            {"SELECT (SELECT MAX(v + o917.i) FROM g917 WHERE g917.k = 2 GROUP BY k) FROM o917", "30"},
            {"SELECT (SELECT MAX(v + o917.i) FROM g917 WHERE g917.k = 2) FROM o917", "30"},
            {"SELECT (SELECT SUM(v + o917.i) FROM g917) FROM o917", "60"},
            {"SELECT (SELECT MAX(v) + o917.i FROM g917 GROUP BY k HAVING k = 2) FROM o917", "30"},
            {"SELECT (SELECT v FROM g917 WHERE g917.k = 2 LIMIT 1) + o917.i FROM o917", "30"},
            {"SELECT (SELECT MAX(v) FROM g917 WHERE g917.k = o917.i + 2 GROUP BY k) FROM m917 WHERE m917.i = 0",
                "REFUSED SQL compilation error: error line 1 at position 47 | invalid identifier 'O917.I'"},
            {"SELECT (SELECT MAX(o917.i) FROM g917 GROUP BY k HAVING k = 2) FROM o917",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT COUNT(*) FROM g917 WHERE g917.k = o917.i + 2 GROUP BY k) FROM o917", "1"},
        });
    }

    @Test
    public void theCallsAndPlacesThatOpenWithAnOuterAggregate() {
        engine.execute("CREATE OR REPLACE TABLE o918 (i INT, a ARRAY)");
        engine.execute("INSERT INTO o918 SELECT 0, ARRAY_CONSTRUCT(1)");
        engine.execute("CREATE OR REPLACE TABLE g918 (k INT, v INT)");
        engine.execute("INSERT INTO g918 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE m918 (i INT)");
        engine.execute("INSERT INTO m918 VALUES (0), (1)");
        engine.execute("CREATE OR REPLACE TABLE e918 (i INT)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(o918.i) FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT COUNT(o918.i) FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(o918.i + 1) FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(o918.i) + 1 FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(5) FROM g918) FROM o918", "5"},
            {"SELECT (SELECT MAX(o918.i) FROM g918 WHERE g918.k = o918.i + 2) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT ARRAY_AGG(o918.i) FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT EXISTS (SELECT MAX(o918.i) FROM g918) FROM o918", "true"},
            {"SELECT (SELECT MAX(v) FROM g918 HAVING MAX(o918.i) > -1) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(m918.i) FROM g918) FROM m918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(o918.a[0]::INT) FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT COUNT(*) + MAX(o918.i) FROM g918) FROM o918", "3"},
            {"SELECT (SELECT MAX(v) + MIN(o918.i) FROM g918) FROM o918", "30"},
            {"SELECT 1 FROM o918 WHERE (SELECT MAX(o918.i) FROM g918) = 0",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(O918.I)] "
                + "can only appear in having or select clause"},
            {"SELECT (SELECT MAX(x.i) FROM g918) FROM (SELECT i FROM o918) x", "0"},
            {"SELECT (SELECT MAX(o918.i) FROM g918), (SELECT MAX(v) + o918.i FROM g918) FROM o918",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
        });
    }

    @Test
    public void anOwnAggregateFirstReadsTheOuterAggregateAsAValue() {
        engine.execute("CREATE OR REPLACE TABLE o919 (i INT)");
        engine.execute("INSERT INTO o919 VALUES (0)");
        engine.execute("CREATE OR REPLACE TABLE g919 (k INT, v INT)");
        engine.execute("INSERT INTO g919 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE m919 (i INT)");
        engine.execute("INSERT INTO m919 VALUES (0), (1)");
        engine.execute("CREATE OR REPLACE TABLE e919 (i INT)");
        assertCells(new String[][] {
            {"SELECT (SELECT COUNT(*) + MAX(m919.i) FROM g919) FROM m919", "4 ;; 4"},
            {"SELECT (SELECT MAX(v) + MIN(m919.i) FROM g919) FROM m919", "30 ;; 30"},
            {"SELECT COUNT(*), (SELECT COUNT(*) + MAX(m919.i) FROM g919) FROM m919", "2|4"},
            {"SELECT m919.i, (SELECT COUNT(*) + MAX(m919.i) FROM g919) FROM m919", "0|4 ;; 1|4"},
            {"SELECT (SELECT COUNT(*) + MAX(m919.i) FROM g919) FROM m919 GROUP BY m919.i ORDER BY 1",
                "REFUSED SQL compilation error: | Subquery containing correlated aggregate function [MAX(M919.I)] "
                + "can only appear in having or select clause"},
            {"SELECT (SELECT COUNT(*) + MAX(e919.i) FROM g919) FROM e919", ""},
            {"SELECT (SELECT COUNT(*) + MAX(m919.i) FROM g919) FROM m919 WHERE FALSE", ""},
            {"SELECT (SELECT COUNT(*) + MAX(o919.i) FROM g919) FROM o919 WHERE FALSE", ""},
            {"SELECT (SELECT MAX(v) + MIN(o919.i) FROM g919) FROM o919, m919", "30 ;; 30"},
            {"SELECT (SELECT COUNT(*) + MAX(o919.i) FROM g919), (SELECT COUNT(*) + MAX(o919.i) FROM g919) FROM o919",
                "3|3"},
            {"SELECT (SELECT COUNT(*) + MAX(m919.i) FROM g919) + 1 FROM m919", "5 ;; 5"},
            {"SELECT (SELECT COUNT(*) + MAX(m919.i) FROM g919) FROM m919 HAVING COUNT(*) > 5", ""},
        });
    }

    @Test
    public void rowsReachingTheSubquery() {
        engine.execute("CREATE OR REPLACE TABLE o920 (i INT)");
        engine.execute("INSERT INTO o920 VALUES (0)");
        engine.execute("CREATE OR REPLACE TABLE g920 (k INT, v INT)");
        engine.execute("INSERT INTO g920 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE m920 (i INT)");
        engine.execute("INSERT INTO m920 VALUES (0), (1)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(o920.i)) FROM o920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT SUM(v + m920.i) FROM g920) FROM m920 WHERE FALSE", ""},
            {"SELECT CASE WHEN o920.i > 5 THEN (SELECT MAX(o920.i) FROM g920) END FROM o920", "null"},
            {"SELECT (SELECT MAX(o920.i) FROM g920 LIMIT 1) FROM o920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT ANY_VALUE(o920.i) FROM g920) FROM o920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(v) FROM g920 HAVING MAX(m920.i) > -1) FROM m920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(x.i) FROM g920) FROM (SELECT i FROM m920) x",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT EXISTS (SELECT MAX(o920.i) FROM g920) FROM o920 WHERE FALSE", ""},
            {"SELECT (SELECT MAX(m920.i) FROM g920) FROM m920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(m920.i)) FROM m920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT MAX(o920.i) FROM g920 WHERE g920.v > 100) FROM o920",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
        });
    }

    @Test
    public void outerNamesTheStatisticsPinToOneValue() {
        engine.execute("CREATE OR REPLACE TABLE o921 (i INT)");
        engine.execute("INSERT INTO o921 VALUES (0)");
        engine.execute("CREATE OR REPLACE TABLE g921 (k INT, v INT)");
        engine.execute("INSERT INTO g921 VALUES (1, 10), (1, 20), (2, 30)");
        engine.execute("CREATE OR REPLACE TABLE p921 (i INT)");
        engine.execute("INSERT INTO p921 VALUES (5), (5)");
        assertCells(new String[][] {
            {"SELECT (SELECT MAX(p921.i) FROM g921) FROM p921",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
            {"SELECT (SELECT COUNT(*) + MAX(p921.i) FROM g921) FROM p921", "8 ;; 8"},
            {"SELECT (SELECT COUNT(*) + MAX(o921.i) FROM g921 WHERE g921.v > o921.i) FROM o921", "3"},
            {"SELECT (SELECT COUNT(*) + MAX(p921.i) FROM g921 WHERE g921.v > p921.i) FROM p921", "8 ;; 8"},
            {"SELECT (SELECT COUNT(*) + MAX(o921.i) FROM g921 WHERE g921.v + o921.i > 0) FROM o921", "3"},
            {"SELECT (SELECT SUM(v + o921.i) FROM g921) FROM o921", "60"},
            {"SELECT (SELECT MAX(v) FROM g921 WHERE g921.v + o921.i > 0) FROM o921", "30"},
            {"SELECT (SELECT MAX(p921.i) + MAX(v) FROM g921) FROM p921",
                "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, position 8"},
        });
    }
}
