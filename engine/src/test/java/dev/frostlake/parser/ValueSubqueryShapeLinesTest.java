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
package dev.frostlake.parser;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A fault inside a query used as a value, where what follows the query's SELECT is the account's own: a name
 * and a word, a literal after a literal, a star, a cast or a CASE around the query, a WITH, an IN list
 * (live-verified).
 */
public class ValueSubqueryShapeLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theFaultInside() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        answer(
            "SELECT (SELECT 1 AS 1, 2)");
        answer(
            "SELECT (SELECT 1, 2 AS 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'x'.",
            "SELECT (SELECT a x y FROM t); SELECT 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT (SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT 1 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'. | syntax error line 1 at position 34 unexpected "
                + "')'.",
            "SELECT (SELECT * FROM t WHERE a = )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 24 unexpected 'y'. | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT CAST((SELECT 1 x y) AS INT); SELECT 1 x y");
        answer(
            "SELECT (SELECT ((EXTRACT('wks' FROM d))) FROM t) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a IN ((SELECT 1 x y), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 29 unexpected 'y'. | syntax error line 1 at position 30 unexpected ')'.",
            "SELECT CASE WHEN (SELECT 1 x y) = 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = (SELECT 1 x y)");
        answer(
            "SELECT 1 FROM t WHERE EXISTS (SELECT 1 x y) AND a = (SELECT 2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'WITH'. | syntax error "
                + "line 1 at position 15 unexpected 'AS'.",
            "SELECT (WITH c AS (SELECT 1 x y) SELECT 1 FROM c)");
        assertAnswer(
            "null",
            "SELECT (SELECT a x FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'x'.",
            "SELECT (SELECT a x y z FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '.'.",
            "SELECT (SELECT t.a x y FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'x'.",
            "SELECT (SELECT a x y FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT 1 2 3)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'.",
            "SELECT (SELECT 'a' 'b')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '1'.",
            "SELECT (SELECT 1 AS 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected '2'.",
            "SELECT (SELECT 1 AS x, 2 AS 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'. | syntax error line 1 at position 34 unexpected "
                + "')'.",
            "SELECT (SELECT * FROM t WHERE a = ) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'. | syntax error line 1 at position 29 unexpected "
                + "')'.",
            "SELECT (SELECT * FROM t WHERE)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'. | syntax error line 1 at position 36 unexpected "
                + "'x'.",
            "SELECT (SELECT * FROM t WHERE a = 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 17 unexpected 'FROM'. | syntax error line 1 at position 21 unexpected "
                + "')'.",
            "SELECT (SELECT * FROM)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 32 unexpected ')'.",
            "SELECT (SELECT a FROM t GROUP BY)");
    }

    @Test
    public void aCastAnInListACaseAndAJoin() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 24 unexpected 'y'. | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT CAST((SELECT 1 x y) AS INT)");
        assertAnswer(
            "1",
            "SELECT CAST((SELECT 1 x) AS INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 28 unexpected 'y'. | syntax error line 1 at position 37 unexpected ')'.",
            "SELECT TRY_CAST((SELECT 1 x y) AS INT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'.",
            "SELECT ABS((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 33 unexpected ')'.",
            "SELECT COALESCE((SELECT 1 x y), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 24 unexpected 'y'. | syntax error line 1 at position 33 unexpected ')'.",
            "SELECT CAST((SELECT 1 x y) AS INT) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a IN ((SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a IN (2, (SELECT 1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 45 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a IN ((SELECT 1 x y), (SELECT 2 z w))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 25 unexpected 'y'.",
            "SELECT a IN ((SELECT 1 x y), 2) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a IN ((SELECT 1 x y), 2) AND 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 29 unexpected 'y'. | syntax error line 1 at position 30 unexpected ')'.",
            "SELECT CASE WHEN (SELECT 1 x y) = 1 THEN 1 ELSE 2 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 33 unexpected 'y'. | syntax error line 1 at position 34 unexpected ')'.",
            "SELECT CASE WHEN 1 = (SELECT 1 x y) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 40 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN (SELECT 1 x y) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT CASE (SELECT 1 x y) WHEN 1 THEN 1 END");
        assertAnswer(
            "1",
            "SELECT CASE WHEN (SELECT 1 x) = 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (SELECT 1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON EXISTS (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = (SELECT 1 x y) WHERE 1 = 1");
    }

    @Test
    public void twoQueriesAndAWith() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 41 unexpected 'y'. | syntax error "
                + "line 1 at position 67 unexpected 'w'.",
            "SELECT 1 FROM t WHERE EXISTS (SELECT 1 x y) AND EXISTS (SELECT 2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 50 unexpected 'SELECT'.",
            "SELECT 1 FROM t WHERE a = (SELECT 1 x y) AND a = (SELECT 2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y), (SELECT 2 z w)");
        answer(
            "SELECT 1 +, (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 64 unexpected 'y'.",
            "SELECT 1 FROM t WHERE a = (SELECT 2 z w) AND EXISTS (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'y'.",
            "SELECT 1 x y, (SELECT 2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'WITH'. | syntax error "
                + "line 1 at position 15 unexpected 'AS'.",
            "SELECT (WITH c AS (SELECT 1) SELECT 1 x y FROM c)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'WITH'. | syntax error "
                + "line 1 at position 15 unexpected 'AS'.",
            "SELECT (WITH c AS (SELECT 1 x y) SELECT 1 FROM c) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'WITH'. | syntax "
                + "error line 1 at position 31 unexpected 'SELECT'.",
            "SELECT 1 WHERE 1 = (WITH c AS (SELECT 1 x y) SELECT 1 FROM c)");
        answer(
            "SELECT (SELECT ((EXTRACT('wks' FROM d))) FROM t)");
        answer(
            "SELECT (SELECT EXTRACT('wks' FROM d) FROM t) FROM t");
        answer(
            "SELECT (SELECT (EXTRACT('wks' FROM d)) FROM t) FROM t");
    }
}
