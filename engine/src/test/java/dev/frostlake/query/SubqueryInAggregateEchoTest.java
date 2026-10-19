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
 * A subquery inside an aggregate or a call, as a refusal echoes it: re-printed from its plan — every item
 * named, the table under its alias, a name of the query around it as CORRELATION(…) — in the key a
 * not-a-valid-order-by-expression refusal names, in a window key and in the argument list an arity refusal
 * tells (live-verified).
 */
public class SubqueryInAggregateEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void anAggregateOverASubquery() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g)), b");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(CORRELATION(FZ.ID) + G.V) AS \"MAX(FZ.ID + G.V)\" "
                + "FROM G AS G))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(fz.id + g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY ABS(MAX((SELECT MAX(g.v) FROM g)))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT G.V AS \"V\" FROM G AS G WHERE G.ID = 5))] is not a "
                + "valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT g.v FROM g WHERE g.id = 5))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT G.V AS \"V\" FROM G AS G WHERE G.ID = "
                + "CORRELATION(FZ.ID)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT g.v FROM g WHERE g.id = fz.id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT COUNT(*) AS \"COUNT(*)\" FROM G AS G WHERE G.ID = "
                + "CORRELATION(FZ.ID)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT COUNT(*) FROM g WHERE g.id = fz.id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G WHERE G.ID > 1))] "
                + "is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(v) FROM g WHERE id > 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(X.V) AS \"MAX(X.V)\" FROM G AS X))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(x.v) FROM g x))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(X.V) AS \"MAX(X.V)\" FROM G AS X WHERE X.ID > 1))] "
                + "is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(x.v) FROM g AS x WHERE x.id > 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"M\" FROM G AS G))] is not a valid order "
                + "by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) AS m FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G WHERE G.ID = "
                + "CORRELATION(FZ.ID)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g WHERE g.id = fz.id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM TEST_SCHEMA.G AS G))] is "
                + "not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM test_schema.g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G GROUP BY G.ID  "
                + "ORDER BY MAX(G.V) ASC NULLS LAST LIMIT 1 OFFSET 0))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g GROUP BY g.id ORDER BY 1 "
                + "LIMIT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT DISTINCT 1 AS \"1\" FROM G AS G))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT DISTINCT 1 FROM g))");
    }

    @Test
    public void otherAggregatesAndShapes() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MIN((SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MIN((SELECT 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | [SUM((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] is not a valid "
                + "order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY SUM((SELECT MAX(g.v) FROM g)) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | [COUNT((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] is not a "
                + "valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY COUNT((SELECT MAX(g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [COUNT(DISTINCT (SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] is "
                + "not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY COUNT(DISTINCT (SELECT MAX(g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(FZ.ID + (SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] is not "
                + "a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(id + (SELECT MAX(g.v) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(CASE_FLATTENED(FZ.ID > (SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G "
                + "AS G), 1, SYSTEM$NULL_TO_FIXED(null)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(CASE WHEN id > (SELECT MAX(g.v) FROM g) THEN 1 END)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G INNER JOIN FZ AS "
                + "F2 ON (F2.ID = G.ID)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g JOIN fz f2 ON f2.id = "
                + "g.id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G INNER JOIN FZ AS "
                + "F2 WHERE F2.ID = G.ID))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g, fz f2 WHERE f2.id = g.id))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX(CAST(FZ.ID = ANY(SELECT G.ID AS \"ID\" FROM G AS G) AS "
                + "BOOLEAN))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX(id IN (SELECT g.id FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G WHERE G.V IN (50, "
                + "60)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g WHERE g.v IN (50, 60)))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G WHERE ((G.V >= 1) "
                + "AND (G.V <= 99)) AND (G.ID IS NOT NULL)))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g WHERE g.v BETWEEN 1 AND 99 "
                + "AND g.id IS NOT NULL))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT CAST(MAX(G.V) AS VARCHAR(134217728)) AS "
                + "\"MAX(G.V)::VARCHAR\" FROM G AS G))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v)::VARCHAR FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT IFNULL(MAX(G.V), 0) AS \"COALESCE(MAX(G.V), 0)\" FROM "
                + "G AS G))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT COALESCE(MAX(g.v), 0) FROM g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM (SELECT G.ID AS \"ID\", "
                + "G.V AS \"V\" FROM G AS G) G))] is not a valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM (SELECT * FROM g) g))");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT SET_COMBINE(WB$0.\"MAX(G.V)\", WB$1.\"1\") AS "
                + "\"MAX(G.V)\" FROM (SELECT OB$0.\"MAX(G.V)\" AS \"MAX(G.V)\" FROM (SELECT MAX(G.V) AS \"MAX(G.V)\" "
                + "FROM G AS G) OB$0) WB$0 UNION ALL (SELECT ENSURE_NULLABLE(CAST(OB$1.\"1\" AS NUMBER(38,0))) AS "
                + "\"1\" FROM (SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL) OB$1) WB$1 LIMIT 1 OFFSET 0))] is not a "
                + "valid order by expression",
            "SELECT DISTINCT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g UNION ALL SELECT 1 LIMIT "
                + "1))");
    }

    @Test
    public void aWindowKey() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G)) OVER ()] is not "
                + "a valid order by expression",
            "SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT MAX(g.v) FROM g)) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [SUM(MAX((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))) OVER ()] "
                + "is not a valid order by expression",
            "SELECT b FROM fz GROUP BY b ORDER BY SUM(MAX((SELECT MAX(g.v) FROM g))) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [SUM(MAX((SELECT MAX(CORRELATION(FZ.ID) + G.V) AS \"MAX(FZ.ID + "
                + "G.V)\" FROM G AS G))) OVER ()] is not a valid order by expression",
            "SELECT b FROM fz GROUP BY b ORDER BY SUM(MAX((SELECT MAX(fz.id + g.v) FROM g))) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [MAX((SELECT G.V AS \"V\" FROM G AS G WHERE G.ID = "
                + "CORRELATION(FZ.ID))) OVER ()] is not a valid order by expression",
            "SELECT b FROM fz GROUP BY b ORDER BY MAX((SELECT g.v FROM g WHERE g.id = fz.id)) OVER ()");
        assertAnswer(
            "REFUSED SQL compilation error: | [SUM(MAX((SELECT MAX(G.V) AS \"MAX(V)\" FROM G AS G WHERE G.ID > "
                + "1))) OVER (PARTITION BY B)] is not a valid order by expression",
            "SELECT b FROM fz GROUP BY b ORDER BY SUM(MAX((SELECT MAX(v) FROM g WHERE id > 1))) OVER (PARTITION "
                + "BY b)");
    }

    @Test
    public void anArityRefusal() {
        answer(
            "CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        answer(
            "INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        answer(
            "CREATE OR REPLACE TABLE g (id INT, v INT)");
        answer(
            "INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer(
            "Function F1 successfully created.",
            "CREATE OR REPLACE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F1((SELECT MAX(CORRELATION(FZ.ID) + G.V) AS \"MAX(FZ.ID + G.V)\" FROM G AS G), 2)] expected 1, "
                + "got 2",
            "SELECT f1((SELECT MAX(fz.id + g.v) FROM g), 2) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F1((SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G), 2)] expected 1, got 2",
            "SELECT f1((SELECT MAX(g.v) FROM g), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F1((SELECT 1 AS \"1\" FROM (VALUES (null)) DUAL), 2)] expected 1, got 2",
            "SELECT f1((SELECT 1), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F1((SELECT G.V AS \"V\" FROM G AS G WHERE G.ID = CORRELATION(FZ.ID)), 2)] expected 1, got 2",
            "SELECT f1((SELECT g.v FROM g WHERE g.id = fz.id), 2) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[F1((SELECT MAX(X.V) AS \"MAX(X.V)\" FROM G AS X WHERE X.ID > 1), 2)] expected 1, got 2",
            "SELECT f1((SELECT MAX(x.v) FROM g x WHERE x.id > 1), 2) FROM fz");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function [F1(1, "
                + "(SELECT MAX(G.V) AS \"MAX(G.V)\" FROM G AS G))] expected 1, got 2",
            "SELECT f1(1, (SELECT MAX(g.v) FROM g))");
        assertAnswer(
            "F1 successfully dropped.",
            "DROP FUNCTION IF EXISTS f1(INT)");
    }
}
