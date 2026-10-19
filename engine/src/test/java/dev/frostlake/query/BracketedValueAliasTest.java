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
 * A word run into a value inside a bracket of a select item — plain parentheses, a call's, a CAST's, an
 * array's. Straight before the bracket's close the word is set aside and the bracket read on; anywhere
 * else the bracket is given up, the word read as the item's alias and the next fault reported, which ends
 * the report: a second word, a literal, an AS, an operator, or the closing parenthesis once a comma or a
 * FROM has been read as the query's own. It holds in the select list of a statement's query, of a derived
 * table and of a common table expression, and not in a WHERE, a GROUP BY, an ORDER BY, a HAVING, a QUALIFY,
 * an UPDATE's SET or a call inside another bracket, where the word stands alone. Every answer is
 * live-verified.
 */
public class BracketedValueAliasTest extends MeasuredCellsTestSupport {

    @Test
    public void theNextFaultIsReportedAndEndsTheReport() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT (1 + 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT ABS(1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y z); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SELECT ARRAY_CONSTRUCT(1 x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "SELECT ABS((1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'.",
            "SELECT 1 WHERE (1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '2'.",
            "SELECT (1 x 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected ''q''.",
            "SELECT (1 x 'q')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 14 unexpected ')'.",
            "SELECT (1 x, y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT 1, (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y), 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected 'y'.",
            "SELECT ABS(1, 2 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'x'. | syntax "
                + "error line 1 at position 14 unexpected 'y'.",
            "SELECT ('a' x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT 1 + (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'AS'.",
            "SELECT (1 x AS y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected ')'.",
            "SELECT (1 x FROM y)");
    }

    @Test
    public void byWhatFollowsTheWordAndWhereTheBracketStands() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'z'.",
            "SELECT (1 x) y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) z w");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'. | syntax "
                + "error line 1 at position 13 unexpected 'y'.",
            "SELECT ((1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT ABS(1 x y) FROM t WHERE a = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y), (2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'z'. | syntax error line 1 at position 19 unexpected 'w'.",
            "SELECT (1 x), (2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'z'.",
            "SELECT (1 x), (2 z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SELECT 1 FROM (SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax "
                + "error line 1 at position 27 unexpected 'y'.",
            "SELECT 1 FROM (SELECT (1 x y)) q");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM (SELECT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 42 unexpected ')'.",
            "SELECT a FROM t WHERE a IN (SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected 'y'.",
            "WITH c AS (SELECT (1 x y)) SELECT 1 FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM t ORDER BY 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT a, (1 x y) AS b, c FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '+'.",
            "SELECT (1 x + y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '('.",
            "SELECT (1 x (y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (a x y) z FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected '\"x\"'. | syntax "
                + "error line 1 at position 14 unexpected 'y'.",
            "SELECT (1 \"x\" y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'AS'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT (1 AS x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'.",
            "SELECT ABS(1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected ')'.",
            "SELECT ABS(1 x, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT CAST(1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT [1 x y]");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) UNION SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "INSERT INTO t SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'. | syntax "
                + "error line 1 at position 30 unexpected 'y'.",
            "CREATE VIEW v9 AS SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'x'.",
            "SELECT 1 FROM t WHERE a = 1 AND (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT 1 FROM t GROUP BY (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT 1 FROM t ORDER BY (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'.",
            "SELECT 1 FROM t HAVING (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'x'.",
            "SELECT 1 FROM t QUALIFY (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) x2 FROM t; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "SELECT * FROM t WHERE (a x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "UPDATE t SET a = (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM t; SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "SELECT 1 x y; SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 24 unexpected 'x'. | syntax error line 1 at position 26 unexpected 'y'.",
            "SELECT (1 x); SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y); SELECT 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y);");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x ;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 24 unexpected 'y'.",
            "SELECT (1 x; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT ABS(1 x y), 2 FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 13 unexpected 'x'.",
            "SELECT ABS(a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'y'.",
            "SELECT IFF(1 x y, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax "
                + "error line 1 at position 22 unexpected 'y'.",
            "SELECT IFF(a = 1, 2 x y, 3) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT ABS(-1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 11 unexpected '.'.",
            "SELECT (1 x.y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y.z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'x'. | syntax "
                + "error line 1 at position 14 unexpected 'y'.",
            "SELECT (t.a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '2'.",
            "SELECT (1 x 2 y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'z'.",
            "SELECT (1 x, 2 y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM t WHERE (2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax "
                + "error line 1 at position 21 unexpected 'y'.",
            "SELECT DISTINCT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected 'y'.",
            "SELECT TOP 1 (1 x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 19 unexpected ')'.",
            "SELECT (1 x LIMIT 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 19 unexpected ')'.",
            "SELECT (1 x WHERE y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 22 unexpected ')'.",
            "SELECT (1 x ORDER BY y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 26 unexpected ')'.",
            "SELECT (1 x UNION SELECT 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'IS'.",
            "SELECT (1 x IS NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 13 unexpected ')'.",
            "SELECT (1 x, )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected ')'.",
            "SELECT (1 x,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '1.5'.",
            "SELECT (1 x 1.5)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'NULL'.",
            "SELECT (1 x NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'TRUE'.",
            "SELECT (1 x TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '*'.",
            "SELECT (1 x *)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '-'.",
            "SELECT (1 x -1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '::'.",
            "SELECT (1 x :: INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected '['.",
            "SELECT (1 x [0])");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'OVER'.",
            "SELECT (1 x OVER ())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected ')'.",
            "SELECT (1 x FROM t) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 14 unexpected ')'.",
            "SELECT (1 x, y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected ')'.",
            "SELECT (1 x INTO y)");
    }

    @Test
    public void afterOperatorsAndBeforeTheClose() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        engine.execute("CREATE OR REPLACE TABLE tt (a INT, s VARCHAR(5), o OBJECT, v VARIANT, tm TIME, d DATE, ts "
            + "TIMESTAMP_NTZ, tz TIMESTAMP_TZ, bo BOOLEAN)");
        engine.execute("INSERT INTO tt SELECT 86400, 'abc', OBJECT_CONSTRUCT('x', '2024-01-02'), PARSE_JSON('{\"x\": "
            + "\"2024-01-02\"}'), '03:04:05'::TIME, '2024-01-02'::DATE, '2024-01-02 03:04:05'::TIMESTAMP_NTZ, "
            + "'2024-01-02 03:04:05 +02:00'::TIMESTAMP_TZ, TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "SELECT (ABS(1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y) FROM t WHERE a = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT 1, 2 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 23 unexpected 'yy'.",
            "SELECT (1 x) FROM t zz yy");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x) 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 29 unexpected 'y'.",
            "SELECT (1 x) 'q'; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax "
                + "error line 1 at position 18 unexpected 'z'.",
            "SELECT ABS(1 x) y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT [1 x]");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 15 unexpected 'z'.",
            "SELECT [1 x] y z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT (1 + 2 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'x'. | syntax "
                + "error line 1 at position 14 unexpected 'y'.",
            "SELECT (a.b x y) FROM t a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'x'. | syntax "
                + "error line 1 at position 14 unexpected 'y'.",
            "SELECT ((1) x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'y'.",
            "SELECT (ABS(1) x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax "
                + "error line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y)::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'. | syntax "
                + "error line 1 at position 13 unexpected 'y'.",
            "SELECT -(1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT NOT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "SELECT 1 = (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "SELECT 1 AS c, (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "SELECT COALESCE(1 x y, 2)");
    }
}
