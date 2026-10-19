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
 * A word run into a value inside a bracket of a select list, for the brackets of a CASE, an IN list, a
 * star's modifiers, a call's quantifier and a scalar subquery, and in a script's last statement
 * (live-verified).
 */
public class SelectBracketWordLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void theBracketsOfASelectList() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT (SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'. | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT CASE WHEN (1 x y) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '='.",
            "SELECT (1 x = y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected 'x'.",
            "SELECT COUNT(DISTINCT a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax error "
                + "line 1 at position 22 unexpected 'x'. | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT 1 +; SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected 'y'. | syntax error line 1 at position 14 unexpected ')'.",
            "SELECT (1 x)(y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'IN'.",
            "SELECT (1 x IN (1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "SELECT ABS(ABS(1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT 1 IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SELECT * EXCLUDE (a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SELECT CASE WHEN (1 x) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'. | syntax error line 1 at position 24 unexpected 'z'.",
            "SELECT CASE WHEN (1 x y z) THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected ')'.",
            "SELECT CASE (1 x y) WHEN 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax error "
                + "line 1 at position 33 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN (1 x y) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'x'. | syntax error "
                + "line 1 at position 40 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN 1 ELSE (1 x y) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'. | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT CASE WHEN (1 x y) THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'. | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT CASE WHEN (a x y) THEN 1 END FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
            "SELECT ABS(ABS(1 x y z))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'x'. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT ABS(ABS(ABS(1 x y)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'.",
            "SELECT ABS((1 x y))");
        answer(
            "SELECT ABS(ABS((1 x y)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT COALESCE(1, ABS(1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'x'. | syntax error "
                + "line 1 at position 15 unexpected 'y'.",
            "SELECT ABS(1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'.",
            "SELECT ABS(ABS(1 x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'.",
            "SELECT 1 IN (1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT 1 IN (1 x, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'x'. | syntax error "
                + "line 1 at position 20 unexpected 'y'.",
            "SELECT 1 IN (1, 2 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'x'. | syntax error "
                + "line 1 at position 18 unexpected 'y'.",
            "SELECT 1 IN ((1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT a IN (1 x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected 'y'.",
            "SELECT 1 NOT IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'IN'.",
            "SELECT (1 x IN (1, 2))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'NOT'.",
            "SELECT (1 x NOT IN (1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'BETWEEN'.",
            "SELECT (1 x BETWEEN 1 AND 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'LIKE'.",
            "SELECT (1 x LIKE 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'IS'.",
            "SELECT (1 x IS NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '+'.",
            "SELECT (1 x + 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '<'.",
            "SELECT (1 x < y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'AND'.",
            "SELECT (1 x AND y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 14 unexpected ')'.",
            "SELECT (1 x, y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '='.",
            "SELECT (1 x = y z)");
    }

    @Test
    public void laterStatementsAndSurplusBrackets() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'.",
            "SELECT 1; SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax error "
                + "line 1 at position 22 unexpected 'x'.",
            "SELECT 1 +; SELECT (1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax error "
                + "line 1 at position 22 unexpected ';'. | syntax error line 1 at position 34 unexpected 'x'. | "
                + "syntax error line 1 at position 36 unexpected 'y'.",
            "SELECT 1 +; SELECT 2 +; SELECT (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y); SELECT (2 z w)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax error "
                + "line 1 at position 25 unexpected 'x'. | syntax error line 1 at position 27 unexpected 'y'.",
            "SELECT 1 +; SELECT ABS(1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax error "
                + "line 1 at position 22 unexpected 'x'. | syntax error line 1 at position 24 unexpected '='.",
            "SELECT 1 +; SELECT (1 x = y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected ';'. | syntax error "
                + "line 1 at position 35 unexpected 'ON'.",
            "SELECT 1 +; SELECT 1 FROM t JOIN n ON (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x)))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'y'.",
            "SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'.",
            "SELECT ((1 x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected 'y'.",
            "SELECT ((1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected 'y'. | syntax error line 1 at position 15 unexpected 'z'.",
            "SELECT (1 x)(y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 14 unexpected 'y'. | syntax error line 1 at position 15 unexpected ')'.",
            "SELECT (1 x) (y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected 'y'. | syntax error line 1 at position 14 unexpected ')'.",
            "SELECT (1 x)(y)(z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x)[0]");
    }

    @Test
    public void quantifiersModifiersConditionsAndSubqueries() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected 'x'.",
            "SELECT COUNT(DISTINCT a x) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected 'x'.",
            "SELECT COUNT(DISTINCT a x y z) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 26 unexpected 'y'.",
            "SELECT COUNT(DISTINCT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'x'.",
            "SELECT SUM(DISTINCT a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 19 unexpected 'x'.",
            "SELECT COUNT(ALL a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 15 unexpected 'x'.",
            "SELECT COUNT(a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SELECT * EXCLUDE (a x) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "SELECT * EXCLUDE (a x, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'x'.",
            "SELECT * EXCLUDE (a, b x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected 'y'.",
            "SELECT * RENAME (a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'.",
            "SELECT * REPLACE (1 x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected 'x'.",
            "SELECT t.* EXCLUDE (a x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT 1 FROM t JOIN n ON 1 x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (t.a = n.a x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'x'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (1 x y) WHERE 1 = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON ABS(1 x y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'ON'.",
            "SELECT 1 FROM t LEFT JOIN n ON (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (1 x y) JOIN n m ON TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON 1 =");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (1 +)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a +");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON t.a = n.a AND (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'ON'.",
            "SELECT 1 FROM t JOIN n ON (1 x y) AND t.a = n.a");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 19 unexpected ')'.",
            "SELECT (SELECT (1 x))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT (SELECT (1 x y) FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 19 unexpected 'y'.",
            "SELECT (SELECT 1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 20 unexpected '1'. | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT 1 + (SELECT (1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected 'a'. | syntax error line 1 at position 18 unexpected 'x'.",
            "SELECT (SELECT (a x y) FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '('. | syntax error line 1 at position 21 unexpected 'y'.",
            "SELECT (SELECT ((1 x y)))");
    }

    @Test
    public void theSameBracketsWider() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '=='.",
            "SELECT (1 x == y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '<>'.",
            "SELECT (1 x <> y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected 'IN'.",
            "SELECT (1 x IN y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '='.",
            "SELECT (1 x = )");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 12 unexpected '='.",
            "SELECT (1 x = y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'. | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT CASE WHEN (1 x y) THEN 1 ELSE 2 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'.",
            "SELECT CASE WHEN 1 = 1 THEN (1 x) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'x'. | syntax error "
                + "line 1 at position 33 unexpected 'y'.",
            "SELECT CASE WHEN 1 = 1 THEN (1 x y z) END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 22 unexpected 'y'. | syntax error line 1 at position 23 unexpected ')'.",
            "SELECT CASE WHEN (1 x y) AND 1 = 1 THEN 1 END");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT 1 IN (1 x y z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'.",
            "SELECT 1 IN (1 x), 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'x'. | syntax error "
                + "line 1 at position 17 unexpected 'y'.",
            "SELECT 1 IN (1 x y), 2");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'x'.",
            "SELECT 1 FROM t WHERE a IN (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT ABS(ABS('a' x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "SELECT ABS(1, ABS(1 x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "SELECT ABS(ABS(1 x y)) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "SELECT ABS(ABS(1 x y), 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x))) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'.",
            "SELECT (1 x)) y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected 'y'. | syntax error line 1 at position 14 unexpected ')'.",
            "SELECT (1 x)(y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected '1'.",
            "SELECT (1 x)(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected ')'.",
            "SELECT (1 x)()");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 10 unexpected 'x'. | syntax error "
                + "line 1 at position 13 unexpected 'y'. | syntax error line 1 at position 17 unexpected ')'.",
            "SELECT (1 x)(y, z)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SELECT * EXCLUDE (a x y z) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'. | syntax error "
                + "line 1 at position 26 unexpected ')'.",
            "SELECT * EXCLUDE (a x, b y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SELECT * EXCLUDE (a x) y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'.",
            "SELECT * EXCLUDE a x y FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'x'.",
            "SELECT * RENAME (a x) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'y'.",
            "SELECT * RENAME (a AS x y) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'x'.",
            "SELECT * REPLACE (1 x) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "SELECT * REPLACE (1 AS a x) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'. | syntax error "
                + "line 1 at position 24 unexpected 'x'.",
            "SELECT COUNT(DISTINCT a x, b) FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'x'. | syntax error "
                + "line 1 at position 28 unexpected 'y'.",
            "SELECT COUNT(DISTINCT 'a' x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 24 unexpected 'x'.",
            "SELECT COUNT(DISTINCT 1 x)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 20 unexpected 'y'.",
            "SELECT (SELECT (1 x y z))");
        assertAnswer(
            "1",
            "SELECT (SELECT (1))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected '1'. | syntax error line 1 at position 19 unexpected ')'.",
            "SELECT (SELECT (1 x) y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected ''a''. | syntax error line 1 at position 22 unexpected 'y'.",
            "SELECT (SELECT ('a' x y))");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected 'a'. | syntax error line 1 at position 18 unexpected 'x'.",
            "SELECT (SELECT (a x) FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 16 unexpected 'a'. | syntax error line 1 at position 18 unexpected 'x'.",
            "SELECT (SELECT (a x y z) FROM t)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "SELECT 1 FROM t WHERE (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'x'.",
            "SELECT 1 FROM t WHERE (a x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT 1 FROM t ORDER BY (1 x y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'x'.",
            "SELECT 1 FROM t GROUP BY (1 x y)");
    }

    @Test
    public void whatStillReads() {
        answer(
            "CREATE OR REPLACE TABLE t (a INT, b VARCHAR, d DATE)");
        answer(
            "CREATE OR REPLACE TABLE n (a INT, f FLOAT)");
        answer(
            "INSERT INTO t VALUES (1, 'x', '2024-01-01')");
        assertAnswer(
            "1|x|2024-01-01",
            "SELECT * RENAME (a AS z) FROM t");
        assertAnswer(
            "1|x|2024-01-01",
            "SELECT * RENAME a AS z FROM t");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'z'.",
            "SELECT * RENAME a z FROM t");
        assertAnswer(
            "1|x|2024-01-01",
            "SELECT * RENAME (a AS z, b AS w) FROM t");
        assertAnswer(
            "x|2024-01-01",
            "SELECT * EXCLUDE (a) RENAME (b AS w) FROM t");
        assertAnswer(
            "1",
            "SELECT a FROM t WHERE a = 1");
        assertAnswer(
            "1",
            "SELECT COUNT(DISTINCT a) FROM t");
        assertAnswer(
            "1",
            "SELECT CASE WHEN (1 = 1) THEN (1) ELSE (2) END");
        assertAnswer(
            "true|1|1",
            "SELECT 1 IN (1, 2), (1), ((1))");
    }
}
