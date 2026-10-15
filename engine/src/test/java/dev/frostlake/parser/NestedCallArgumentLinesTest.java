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
 * A stray word after an argument of a call inside another call, after a literal argument and after an empty name
 * part: the lines the account stacks after the first (live-verified).
 */
public class NestedCallArgumentLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void anEmptyNamePartAndARetriedArgument() {
        answer(
            "CREATE OR REPLACE TABLE T (x INT, y INT)");
        answer(
            "INSERT INTO T VALUES (1, 2)");
        answer(
            "SELECT ABS((T..x)) FROM T");
        answer(
            "SELECT CAST((T..x) AS INT) FROM T");
        answer(
            "SELECT IFF(ABS(T..x) > 0, 1, 2) FROM T");
        answer(
            "SELECT CASE WHEN ABS(T..x) = 1 THEN 1 END FROM T");
        answer(
            "SELECT (SELECT ABS(T..x) FROM T)");
        answer(
            "SELECT 1 FROM T WHERE x IN (SELECT ABS(T..x) FROM T)");
        answer(
            "EXECUTE IMMEDIATE $$BEGIN\nLET x INT := (SELECT T..x FROM T);\nEND$$");
        answer(
            "SELECT COUNT(T..x) FROM");
        answer(
            "SHOW TABLES IN a...c y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected 'y'.",
            "SELECT ABS(ABS(T.x y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT ABS(ABS(x y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "SELECT ABS(ABS(ABS(x y))) FROM T");
        answer(
            "SELECT ABS(ABS(T.x.)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 27 unexpected 'y'. | syntax error "
                + "line 1 at position 27 unexpected 'y'.",
            "SELECT CONCAT('a', ABS(T.x y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT ABS(1 y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'.",
            "SELECT ABS('a' y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 15 unexpected 'z'.",
            "SELECT ABS(1 y z) FROM T");
    }

    @Test
    public void anArgumentThatOpensWithALiteral() {
        answer(
            "CREATE OR REPLACE TABLE T (x INT, y INT)");
        answer(
            "INSERT INTO T VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 15 unexpected 'z'.",
            "SELECT ABS(1 y z w) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'.",
            "SELECT ABS(1.5 y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'y'. | syntax error "
                + "line 1 at position 16 unexpected 'y'.",
            "SELECT ABS(TRUE y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'y'.",
            "SELECT ABS(NULL y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 21 unexpected ')'.",
            "SELECT UPPER('a' y, 1) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT ABS(1 y) + 1 FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'y'.",
            "SELECT 1, ABS(1 y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 21 unexpected 'w'.",
            "SELECT ABS(1 y), x z w FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT ABS(-1 y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 31 unexpected 'y'.",
            "SELECT ABS(1 y) FROM T WHERE x y");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'AS'. | syntax error "
                + "line 1 at position 17 unexpected ')'.",
            "SELECT ABS(1 AS y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 17 unexpected ')'.",
            "SELECT ABS(1 y, 2) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "SELECT COALESCE(1 y, 2 z) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 20 unexpected '<EOF>'.",
            "SELECT ABS(1 y) FROM");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'.",
            "SELECT ABS(1 y)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'y'.",
            "SELECT x FROM T WHERE ABS(1 y) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'y'.",
            "SELECT ABS(DATE '2020-01-01' y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'.",
            "SELECT ABS(1 + 2 y) FROM T");
    }

    @Test
    public void aCallInsideAnotherCall() {
        answer(
            "CREATE OR REPLACE TABLE T (x INT, y INT)");
        answer(
            "INSERT INTO T VALUES (1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'.",
            "SELECT ABS(ABS(x y z)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax error "
                + "line 1 at position 24 unexpected ')'.",
            "SELECT ABS(UPPER(x y), 1) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT ABS(ABS(x y)) + 1 FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 22 unexpected ')'.",
            "SELECT ABS(ABS(x y), 2) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 21 unexpected 'y'. | syntax error "
                + "line 1 at position 23 unexpected ')'.",
            "SELECT 1 + ABS(ABS(x y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'.",
            "SELECT ABS(ABS(1 y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'.",
            "SELECT ABS(ABS('a' y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected 'y'.",
            "SELECT ABS(ABS(T.x y), 1) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT IFF(ABS(x y) > 0, 1, 2) FROM T");
        answer(
            "SELECT ABS((x y)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT ABS(ABS(x y)) FROM T WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT ABS(ABS(x y)), ABS(ABS(x z)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected 'y'. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT ABS(ABS(ABS(ABS(x y)))) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected 'y'.",
            "SELECT ABS(ABS(T.x y z)) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 23 unexpected 'y'. | syntax error "
                + "line 1 at position 25 unexpected ')'.",
            "SELECT ABS(ABS(ABS(T.x y))) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 13 unexpected 'y'. | syntax error "
                + "line 1 at position 13 unexpected 'y'.",
            "SELECT ABS(x y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 15 unexpected 'y'. | syntax error "
                + "line 1 at position 12 unexpected '.'.",
            "SELECT ABS(T.x y) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected 'y'. | syntax error "
                + "line 1 at position 19 unexpected ')'.",
            "SELECT ABS(ABS(x y)) AS a FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'. | syntax error "
                + "line 1 at position 27 unexpected ')'.",
            "SELECT CAST(ABS(x y) AS INT) FROM T");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'y'.",
            "SELECT x FROM T WHERE ABS(ABS(x y)) = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected 'y'.",
            "SELECT ABS(ABS(x) y) FROM T");
    }

    @Test
    public void aScopeNameWithEmptyParts() {
        answer(
            "CREATE OR REPLACE TABLE T (x INT, y INT)");
        answer(
            "INSERT INTO T VALUES (1, 2)");
        answer(
            "SELECT SHOW TABLES IN a...c");
        answer(
            "SHOW TABLES IN a...c");
        answer(
            "SHOW TABLES IN a....c y");
        assertAnswer(
            "REFUSED syntax error line 1 at position 15 unexpected 'a'.",
            "SHOW TABLES IN a..c y");
        assertAnswer(
            "REFUSED syntax error line 1 at position 15 unexpected 'a.b.c'.",
            "SHOW TABLES IN a.b.c y");
    }
}
