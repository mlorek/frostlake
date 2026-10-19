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
 * A fault inside a parenthesized query used as a value — a scalar subquery, the query of an IN or of a
 * quantified comparison — is refused at that query's SELECT first, once for each such query around the
 * fault. In a select list the fault inside is reported next and the report ends; as a call's argument
 * or more than two brackets deep the SELECT stands alone; in a WHERE, a HAVING, a QUALIFY, a GROUP BY, an
 * ORDER BY, an UPDATE's SET, a VALUES row or a CALL the fault inside is not reported and the later
 * statements are read. A derived table, a common table expression's body and an EXISTS report their own
 * fault. Every answer is live-verified.
 */
public class ValueSubqueryLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theSelectIsRefusedFirst() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        engine.execute("INSERT INTO n VALUES (86400, 1.5)");
        engine.execute("INSERT INTO t VALUES (1, 'x', '2024-01-02')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT 1 +)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SELECT (SELECT 1 FROM n x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '1'.",
            "SELECT (SELECT 1 AS 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '1'. | syntax error line 1 at position 28 unexpected '2'.",
            "SELECT (SELECT 1 AS 1, 2 AS 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 + (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 22 unexpected 'y'.",
            "SELECT 1, (SELECT 1 x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 + (SELECT 1 x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT 1 IN (SELECT 1 x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected 'SELECT'. | syntax error line 1 at position 27 "
                + "unexpected 'y'.",
            "SELECT (SELECT (SELECT 1 x y)); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 32 unexpected 'x'.",
            "SELECT (SELECT 1 FROM t WHERE a x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 24 unexpected '<EOF>'.",
            "SELECT a, (SELECT a FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'.",
            "SELECT 1 WHERE (SELECT 1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'.",
            "SELECT 1 FROM n WHERE a = (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 53 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1 x y); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 35 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 65 unexpected 'y'.",
            "SELECT a FROM t GROUP BY a HAVING (SELECT 1 x y) = 1; SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'.",
            "SELECT UPPER((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 34 unexpected 'y'.",
            "SELECT 1 FROM (SELECT (SELECT 1 x y)); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'y'.",
            "SELECT EXISTS (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'.",
            "SELECT 1 FROM n WHERE EXISTS (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'SELECT'.",
            "SELECT 1 WHERE a IN (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected '1'.",
            "SELECT a FROM (SELECT 1 AS 1)");
        assertAnswer(
            "1",
            "SELECT (SELECT 1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected 'y'.",
            "SELECT 1 IN ((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 39 unexpected 'y'.",
            "SELECT CASE WHEN TRUE THEN (SELECT 1 x y) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 23 unexpected 'y'.",
            "SELECT 1 = (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'.",
            "SELECT 1 FROM t ORDER BY (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'.",
            "SELECT 1 FROM t GROUP BY (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'SELECT'.",
            "SELECT 1 FROM t QUALIFY (SELECT 1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'.",
            "SELECT ABS((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 33 unexpected ')'.",
            "SELECT COALESCE((SELECT 1 x y), 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected ')'.",
            "SELECT IFF((SELECT 1 x y) = 1, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT 1 FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 29 unexpected ')'.",
            "SELECT (SELECT 1 FROM t WHERE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected ')'.",
            "SELECT (SELECT 1 FROM t JOIN)");
        assertAnswer(
            "1",
            "SELECT (SELECT 1,)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SELECT (SELECT 1 FROM t x y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'.",
            "INSERT INTO t (a) VALUES ((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'SELECT'.",
            "UPDATE t SET a = (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1 x y) AND b = 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE (SELECT 1 x y) IS NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a > ALL (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a BETWEEN (SELECT 1 x y) AND 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "SELECT -(SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) + 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) AS c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y), (SELECT 2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'.",
            "SELECT a FROM t WHERE a = (SELECT 1 +)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'.",
            "SELECT a FROM t WHERE a = (SELECT 1 FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'SELECT'.",
            "DELETE FROM t WHERE a = (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 63 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1 x y); SELECT 2; SELECT 3 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 30 unexpected 'y'.",
            "WITH c AS (SELECT (SELECT 1 x y)) SELECT 1 FROM c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "SELECT ((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'SELECT'.",
            "SELECT (((SELECT 1 x y)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected 'y'.",
            "SELECT [(SELECT 1 x y)]");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 25 unexpected 'y'.",
            "SELECT {'a': (SELECT 1 x y)}");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y)::INT");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 23 unexpected 'y'.",
            "SELECT NOT (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) IN (1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected '('.",
            "SELECT 1 LIMIT (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "CALL p((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 9 unexpected 'SELECT'.",
            "SET v = (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 0 unexpected 'VALUES'.",
            "VALUES ((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) FROM t WHERE a = 1 z");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 26 unexpected 'y'.",
            "SELECT (SELECT 1 FROM t x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y) x2 y2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'SELECT'.",
            "SELECT a, (SELECT 1 +) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 36 unexpected '2'.",
            "SELECT (SELECT 1 FROM t WHERE a = 1 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 32 unexpected ')'.",
            "SELECT (SELECT 1 FROM t ORDER BY)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 34 unexpected 'y'.",
            "SELECT (SELECT 1 UNION SELECT 2 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 49 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1 x y) OR a = (SELECT 2 z w)");
        assertAnswer(
            "1",
            "SELECT 1 FROM t WHERE a = (SELECT 1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE ABS((SELECT 1 x y)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a = 1 + (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'SELECT'.",
            "SELECT 1 FROM t HAVING (SELECT 1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 35 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a = (SELECT (SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE (a, b) = (SELECT 1 x y)");
    }
}
