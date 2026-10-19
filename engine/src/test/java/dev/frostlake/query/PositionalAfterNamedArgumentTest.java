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
 * A call that writes a positional argument after a named one is read whole and refused by its name while the
 * statement compiles: {@code positional argument cannot follow named argument in function call 'N1'}, at the
 * argument. A built-in, an aggregate, a window function, a user's function and a name nobody defined are refused
 * alike, before any table or column of the statement is looked up and after every syntax error. The name is the
 * last part the call writes, as it resolves. The place is the argument's own: the operator of an infix or a
 * postfix form, the first token of any other, parentheses not counted. Of several such calls the first in the
 * text is refused, a call after the calls it holds and a WITH clause's queries after the statement they head.
 * Every answer is live-verified.
 */
public class PositionalAfterNamedArgumentTest extends MeasuredCellsTestSupport {

    @Test
    public void aPositionalArgumentAfterANamedOneIsRefusedByTheCallsName() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x INT) RETURNS INT AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION pick(x VARCHAR) RETURNS VARCHAR AS 'x'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]')");
        answer(
            "CREATE OR REPLACE PROCEDURE pa(a INT, b VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END;'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(b => 1, 2)");
        assertAnswer(
            "3",
            "SELECT n1(1, b => 2)");
        assertAnswer(
            "3",
            "SELECT n1(a => 1, b => 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2, 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2, b => 3)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(1, a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT UPPER(x => 'a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | function UPPER does not support named "
                + "arguments",
            "SELECT UPPER(x => 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'NOSUCHFN'",
            "SELECT nosuchfn(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) FROM nosuch_table");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2), nosuchcol FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT nosuchcol, n1(a => 1, 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 34 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT 1 FROM t1 WHERE n1(a => 1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 42 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT nosuchcol FROM t1 WHERE n1(a => 1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT UPPER(n1(a => 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT n1(a => UPPER(x => 'a', 'b'), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) + ABS(1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT ABS(1, 2) + n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | positional argument cannot follow named "
                + "argument in function call 'COUNT'",
            "SELECT COUNT(x => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | positional argument cannot follow named "
                + "argument in function call 'COUNT'",
            "SELECT COUNT(x => 1, 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'SUM'",
            "SELECT SUM(x => c, 2) OVER () FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'LISTAGG'",
            "SELECT LISTAGG(x => s, ',') FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | positional argument cannot follow named "
                + "argument in function call 'COUNT'",
            "SELECT COUNT(DISTINCT x => c, 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | positional argument cannot follow named "
                + "argument in function call 'IFF'",
            "SELECT IFF(a => TRUE, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'COALESCE'",
            "SELECT COALESCE(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'NVL'",
            "SELECT NVL(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'CONCAT'",
            "SELECT CONCAT(a => 'x', 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | positional argument cannot follow named "
                + "argument in function call 'OBJECT_CONSTRUCT'",
            "SELECT OBJECT_CONSTRUCT(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | positional argument cannot follow named "
                + "argument in function call 'ARRAY_CONSTRUCT'",
            "SELECT ARRAY_CONSTRUCT(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'GREATEST'",
            "SELECT GREATEST(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'TO_CHAR'",
            "SELECT TO_CHAR(x => 1, '9')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | positional argument cannot follow named "
                + "argument in function call 'SUBSTR'",
            "SELECT SUBSTR(a => 'abc', 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | positional argument cannot follow named "
                + "argument in function call 'TRIM'",
            "SELECT TRIM(a => 'x', 'y')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | positional argument cannot follow named "
                + "argument in function call 'DECODE'",
            "SELECT DECODE(x => 1, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | positional argument cannot follow named "
                + "argument in function call 'CURRENT_DATE'",
            "SELECT CURRENT_DATE(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | positional argument cannot follow named "
                + "argument in function call 'DATEADD'",
            "SELECT DATEADD(x => 'day', 1, CURRENT_DATE())");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected '<EOF>'.",
            "SELECT n1(a => 1, 2) FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected '3'.",
            "SELECT n1(a => 1, 2 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected ')'.",
            "SELECT n1(a => 1, )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected '<EOF>'.",
            "SELECT n1(a => 1, 2) AS");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'PICK'",
            "SELECT pick(x => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 30 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT test_schema.n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 38 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT test_db.test_schema.n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT \"N1\"(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'n1'",
            "SELECT \"n1\"(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 2 at position 1 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1,\n 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(\"a\" => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 47 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "CREATE OR REPLACE VIEW vv AS SELECT n1(a => 1, 2) x");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: error line 1 at position 12 | "
                + "positional argument cannot follow named argument in function call 'N1'",
            "CREATE OR REPLACE FUNCTION fb() RETURNS INT AS 'n1(a => 1, 2)'");
        assertAnswer(
            "REFUSED Compilation of SQL UDF failed: SQL compilation error: error line 2 at position 17 | "
                + "positional argument cannot follow named argument in function call 'N1'",
            "CREATE OR REPLACE FUNCTION fb2() RETURNS INT AS $$\n  1 + n1(a => 1, 2)$$");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM t1 ORDER BY n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 46 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT c FROM t1 GROUP BY c HAVING n1(a => 1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 28 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT CASE WHEN n1(a => 1, 2) > 0 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT (SELECT n1(a => 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) IN (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT CAST(n1(a => 1, 2) AS VARCHAR)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2)::VARCHAR");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT -n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "EXPLAIN SELECT n1(a => 1, 2)");
        answer(
            "SELECT m!PREDICT(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES (n1(a => 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 33 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM (SELECT n1(a => 1, 2) x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "WITH q AS (SELECT n1(a => 1, 2) x) SELECT * FROM q");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) UNION ALL SELECT nosuchcol");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 45 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT nosuchcol UNION ALL SELECT n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 26 | positional argument cannot follow named "
                + "argument in function call 'NOSUCH_ONE'",
            "SELECT nosuch_one(a => 1, 2), UPPER(x => 'a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT 1 WHERE UPPER(x => 'a', 'b') = 'A' AND n1(a => 1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) x, x + 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, (2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, t1.c) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 'txt')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, -2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 1 + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, ABS(2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, (SELECT 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, CASE WHEN TRUE THEN 2 END)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, :b)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, ?)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, $1)");
        answer(
            "SELECT n1(a => 1, *)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'DISTINCT'.",
            "SELECT n1(a => 1, DISTINCT 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "INSERT INTO t1 (c) SELECT n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "UPDATE t1 SET c = n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 32 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "DELETE FROM t1 WHERE n1(a => 1, 2) = 0");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM t1");
    }

    @Test
    public void theRefusalStandsAtTheArgumentsOwnPlace() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION f2() RETURNS INT AS '1'");
        answer(
            "CREATE OR REPLACE FUNCTION jsf() RETURNS FLOAT LANGUAGE JAVASCRIPT AS 'return 1;'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR, v VARIANT, b BOOLEAN)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x', PARSE_JSON('[1,2]'), TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 1 + 2 * 3) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 1 * 2 + 3) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c::INT) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, CAST(c AS INT)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c IS NULL) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, NOT TRUE) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c IN (1, 2)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c BETWEEN 1 AND 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, s LIKE 'x') FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 'a' || 'b') FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c = 1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, b AND TRUE) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, v:k) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, v[0]) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, ((2))) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, (1 + 1)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, -c) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, - 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, +2) FROM t1");
        answer(
            "SELECT n1(a => 1, DATE '2020-01-01') FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, [1, 2]) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, {'a': 1}) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, EXISTS (SELECT 1)) FROM t1");
        answer(
            "SELECT n1(a => 1, s COLLATE 'en') FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 27 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, COUNT(*) OVER ()) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, CURRENT_DATE) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, TRUE) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 1.5e3) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, v:k::INT) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 1 = 1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, NOT b) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c % 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c / 2 - 1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, (c)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, t1.c + 1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c IS NOT NULL) FROM t1");
        answer(
            "SELECT n1(a => 1, c NOT IN (1)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, s ILIKE 'x') FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c <> 1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, b OR b) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, TRY_CAST(s AS INT)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c::INT::VARCHAR) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, CASE c WHEN 1 THEN 2 END) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, (SELECT MAX(c) FROM t1)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 25 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, s || s || s) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 1 + 2 + 3) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 20 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c - -1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 19 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, v:a.b) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 21 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, v:a[0]) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 22 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, NULL::INT) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 24 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c > 1 AND c < 3) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, IFF(b, 1, 2) + 1) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, n1(1, 2)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, n1(a => 1, b => 2)) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 34 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT n1(a => 1, UPPER(x => 'a', 'b'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 37 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT n1(a => 1, UPPER(s, x => 'b', 'c')) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 31 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT n1(a => UPPER(x => 'a', 'b'), UPPER(y => 'c', 'd'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) FROM t1 WHERE UPPER(x => 'a', 'b') = 'A'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 39 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT 1 FROM t1 WHERE UPPER(x => 'a', 'b') = 'A' ORDER BY n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM t1 x JOIN t1 y ON n1(a => 1, 2) = 1 WHERE UPPER(x => 'a', 'b') = 'A'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT UPPER(x => 'a', 'b') FROM t1 x JOIN t1 y ON n1(a => 1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 58 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "WITH q AS (SELECT n1(a => 1, 2) x) SELECT UPPER(x => 'a', 'b') FROM q");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "SELECT UPPER(x => 'a', 'b') FROM (SELECT n1(a => 1, 2) x)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[1]'), 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, c, c) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 23 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(c, a => 1, c + 1, c + 2) FROM t1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 53 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "CREATE OR REPLACE TABLE tz (c INT DEFAULT n1(a => 1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 48 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "CREATE OR REPLACE TABLE tz AS SELECT n1(a => 1, 2) c");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 49 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "MERGE INTO t1 USING (SELECT 1 k) q ON n1(a => 1, 2) = q.k WHEN MATCHED THEN UPDATE SET c = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 50 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM t1 WHERE c = ANY (SELECT n1(a => 1, 2))");
        assertAnswer(
            "1",
            "SELECT COUNT(*) FROM t1");
    }

    @Test
    public void aWithClauseIsReadAfterTheStatementItHeads() {
        answer(
            "CREATE OR REPLACE FUNCTION n1(a INT, b INT) RETURNS INT AS 'a + b'");
        answer(
            "CREATE OR REPLACE FUNCTION tov(a INT, b VARCHAR) RETURNS TABLE (v INT) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE FUNCTION tn(a NUMBER(5,2)) RETURNS TABLE (v NUMBER(5,2)) AS 'SELECT a'");
        answer(
            "CREATE OR REPLACE TABLE t1 (c INT, s VARCHAR)");
        answer(
            "INSERT INTO t1 SELECT 1, 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 73 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "WITH q AS (SELECT n1(a => 1, 2) x) SELECT 1 FROM q WHERE UPPER(x => 'a', 'b') = 'A'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 76 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "WITH q AS (SELECT n1(a => 1, 2) x) SELECT 1 FROM q ORDER BY UPPER(x => 'a', 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 29 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "WITH q AS (SELECT n1(a => 1, 2) x), r AS (SELECT UPPER(x => 'a', 'b') y) SELECT 1 FROM r, q");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 74 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "WITH q AS (SELECT n1(a => 1, 2) x) SELECT 1 FROM t1 WHERE UPPER(x => 'a', 'b') = 'A'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 66 | positional argument cannot follow named "
                + "argument in function call 'UPPER'",
            "WITH q AS (SELECT n1(a => 1, 2) x) SELECT (SELECT UPPER(x => 'a', 'b')) FROM q");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT 1 FROM (WITH q AS (SELECT n1(a => 1, 2) x) SELECT * FROM q) WHERE UPPER(x => 'a', 'b') = 'A'");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT 1 FROM nosuch_table WHERE n1(a => 1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 34 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT 1 FROM t1 WHERE n1(a => 1, 2) > 0 AND nosuchcol = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 44 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT nosuchfn(1) FROM t1 WHERE n1(a => 1, 2) > 0");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 53 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT 1 FROM t1 a JOIN nosuch_table b ON n1(a => 1, 2) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 43 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "INSERT INTO nosuch_table SELECT n1(a => 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 40 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM TABLE(tov(a => n1(a => 1, 2), 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 35 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT * FROM TABLE(tov(n1(a => 1, 2), b => 'x'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 60 | positional argument cannot follow named "
                + "argument in function call 'PARSE_JSON'",
            "SELECT * FROM TABLE(FLATTEN(INPUT => PARSE_JSON(x => '[1]', 2)))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 18 | positional argument cannot follow named "
                + "argument in function call 'N1'",
            "SELECT n1(a => 1, 2) FROM TABLE(tov(1, 'x'))");
    }
}
